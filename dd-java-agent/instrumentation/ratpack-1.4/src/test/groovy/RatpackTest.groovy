import datadog.opentracing.scopemanager.ContextualScopeManager
import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.utils.OkHttpUtils
import datadog.trace.api.DDSpanTypes
import datadog.trace.instrumentation.ratpack.impl.RatpackScopeManager
import io.opentracing.Scope
import io.opentracing.util.GlobalTracer
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import ratpack.exec.Promise
import ratpack.exec.util.ParallelBatch
import ratpack.groovy.test.embed.GroovyEmbeddedApp
import ratpack.http.HttpUrlBuilder
import ratpack.http.client.HttpClient
import ratpack.path.PathBinding
import ratpack.test.exec.ExecHarness

class RatpackTest extends AgentTestRunner {
  static {
    System.setProperty("dd.integration.ratpack.enabled", "true")
  }

  OkHttpClient client = OkHttpUtils.client()


  def "test path call"() {
    setup:
    def app = GroovyEmbeddedApp.ratpack {
      handlers {
        get {
          context.render("success")
        }
      }
    }
    def request = new Request.Builder()
      .url(app.address.toURL())
      .get()
      .build()
    when:
    def resp = client.newCall(request).execute()
    then:
    resp.code() == 200
    resp.body.string() == "success"

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          operationName "ratpack.handler"
          errored false
          tags {
            "resource.name" "GET /"
            "component" "handler"
            "http.url" "/"
            "http.method" "GET"
            "span.kind" "server"
            "http.status_code" 200
          }
        }
      }
    }
  }

  def "test path with bindings call"() {
    setup:
    def app = GroovyEmbeddedApp.ratpack {
      handlers {
        prefix(":foo/:bar?") {
          get("baz") { ctx ->
            context.render(ctx.get(PathBinding).description)
          }
        }
      }
    }
    def request = new Request.Builder()
      .url(HttpUrl.get(app.address).newBuilder().addPathSegments("a/b/baz").build())
      .get()
      .build()
    when:
    def resp = client.newCall(request).execute()
    then:
    resp.code() == 200
    resp.body.string() == ":foo/:bar?/baz"

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          operationName "ratpack.handler"
          errored false
          tags {
            "resource.name" "GET /:foo/:bar?/baz"
            "component" "handler"
            "http.url" "/a/b/baz"
            "http.method" "GET"
            "span.kind" "server"
            "http.status_code" 200
          }
        }
      }
    }
  }

  def "test error response"() {
    setup:
    def app = GroovyEmbeddedApp.ratpack {
      handlers {
        get {
          context.clientError(500)
        }
      }
    }
    def request = new Request.Builder()
      .url(app.address.toURL())
      .get()
      .build()
    when:
    def resp = client.newCall(request).execute()
    then:
    resp.code() == 500

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          operationName "ratpack.handler"
          errored true
          tags {
            "resource.name" "GET /"
            "component" "handler"
            "http.url" "/"
            "http.method" "GET"
            "span.kind" "server"
            "error" true
            "http.status_code" 500
          }
        }
      }
    }
  }

  def "test path call using ratpack http client"() {
    setup:

    def external = GroovyEmbeddedApp.ratpack {
      handlers {
        get("nested") {
          context.render("succ")
        }
        get("nested2") {
          context.render("ess")
        }
      }
    }

    def app = GroovyEmbeddedApp.ratpack {
      handlers {
        get { HttpClient httpClient ->
          // 1st internal http client call to nested
          httpClient.get(HttpUrlBuilder.base(external.address).path("nested").build())
            .map { it.body.text }
            .flatMap { t ->
            // make a 2nd http request and concatenate the two bodies together
            httpClient.get(HttpUrlBuilder.base(external.address).path("nested2").build()) map { t + it.body.text }
          }
          .then {
            context.render(it)
          }
        }
      }
    }
    def request = new Request.Builder()
      .url(app.address.toURL())
      .get()
      .build()
    when:
    def resp = client.newCall(request).execute()
    then:
    resp.code() == 200
    resp.body().string() == "success"

    assertTraces(1) {
      trace(0, 5) {
        span(0) {
          operationName "ratpack.handler"
          errored false
          tags {
            "resource.name" "GET /"
            "component" "handler"
            "http.url" "/"
            "http.method" "GET"
            "span.kind" "server"
            "http.status_code" 200
          }
        }
        span(1) {
          operationName "ratpack.client-request"
          errored false
          tags {
            "component" "httpclient"
            "http.url" "${external.address}nested2"
            "http.method" "GET"
            "span.kind" "client"
            "http.status_code" 200
          }
        }
        span(2) {
          operationName "ratpack.handler"
          errored false
          tags {
            "resource.name" "GET /nested2"
            "component" "handler"
            "http.url" "/nested2"
            "http.method" "GET"
            "span.kind" "server"
            "http.status_code" 200
          }
        }
        span(3) {
          operationName "ratpack.client-request"
          errored false
          tags {
            "component" "httpclient"
            "http.url" "${external.address}nested"
            "http.method" "GET"
            "span.kind" "client"
            "http.status_code" 200
          }
        }
        span(4) {
          operationName "ratpack.handler"
          errored false
          tags {
            "resource.name" "GET /nested"
            "component" "handler"
            "http.url" "/nested"
            "http.method" "GET"
            "span.kind" "server"
            "http.status_code" 200
          }
        }
      }
    }

  }

  def "forked executions inherit parent scope"() {
    when:
    def result = ExecHarness.yieldSingle({ spec ->
      // This does the work of the initial instrumentation that occurs on the server registry. Because we are using
      // ExecHarness for testing this does not get executed by the instrumentation
      def ratpackScopeManager = new RatpackScopeManager()
      spec.add(ratpackScopeManager)
      ((ContextualScopeManager) GlobalTracer.get().scopeManager())
        .addScopeContext(ratpackScopeManager)
    }, {
      final Scope scope =
        GlobalTracer.get()
          .buildSpan("ratpack.exec-test")
          .startActive(true)
      scope.span().setBaggageItem("test-baggage", "foo")
      ParallelBatch.of(testPromise(), testPromise()).yield()
    })

    then:
    result.valueOrThrow == ["foo", "foo"]
  }

  Promise<String> testPromise() {
    Promise.sync {
      GlobalTracer.get().activeSpan().getBaggageItem("test-baggage")
    }
  }
}
