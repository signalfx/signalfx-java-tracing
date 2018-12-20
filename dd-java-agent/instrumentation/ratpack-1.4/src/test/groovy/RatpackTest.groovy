// Modified by SignalFx
import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.utils.OkHttpUtils
import datadog.trace.context.TraceScope
import io.opentracing.Scope
import io.opentracing.tag.Tags
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
          operationName "GET /"
          parent()
          errored false
          tags {
            "$Tags.COMPONENT.key" "ratpack"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_SERVER
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.HTTP_STATUS.key" 200
            "$Tags.HTTP_URL.key" "/"
            defaultTags()
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
          operationName "GET /:foo/:bar?/baz"
          parent()
          errored false
          tags {
            "$Tags.COMPONENT.key" "ratpack"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_SERVER
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.HTTP_STATUS.key" 200
            "$Tags.HTTP_URL.key" "/a/b/baz"
            defaultTags()
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
          context.render(Promise.sync {
            return "fail " + 0 / 0
          })
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
          operationName "GET /"
          parent()
          errored true
          tags {
            "$Tags.COMPONENT.key" "ratpack"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_SERVER
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.HTTP_STATUS.key" 500
            "$Tags.HTTP_URL.key" "/"
            "error" true
//            errorTags(Exception, String) // TODO: find out how to get throwable in instrumentation
            defaultTags()
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
          operationName "GET /"
          errored false
          tags {
            "component" "ratpack"
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
            "component" "ratpack-httpclient"
            "http.url" "${external.address}nested2"
            "http.method" "GET"
            "span.kind" "client"
            "http.status_code" 200
          }
        }
        span(2) {
          operationName "GET /nested2"
          errored false
          tags {
            "component" "ratpack"
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
            "component" "ratpack-httpclient"
            "http.url" "${external.address}nested"
            "http.method" "GET"
            "span.kind" "client"
            "http.status_code" 200
          }
        }
        span(4) {
          operationName "GET /nested"
          errored false
          tags {
            "component" "ratpack"
            "http.url" "/nested"
            "http.method" "GET"
            "span.kind" "server"
            "http.status_code" 200
          }
        }
      }
    }

    where:
    startSpanInHandler << [true, false]
  }

  def "forked executions inherit parent scope"() {
    when:
    def result = ExecHarness.yieldSingle({}, {
      final Scope scope =
        GlobalTracer.get()
          .buildSpan("ratpack.exec-test")
          .withTag("resource.name", "INSIDE-TEST")
          .startActive(true)

      ((TraceScope) scope).setAsyncPropagation(true)
      scope.span().setBaggageItem("test-baggage", "foo")
      ParallelBatch.of(testPromise(), testPromise())
        .yield()
        .map({ now ->
        // close the scope now that we got the baggage inside the promises
        scope.close()
        return now
      })
    })

    then:
    result.valueOrThrow == ["foo", "foo"]
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          operationName "ratpack.exec-test"
          parent()
          errored false
          tags {
            "resource.name" "INSIDE-TEST"
            defaultTags()
          }
        }
      }
    }
  }

  // returns a promise that contains the active scope's "test-baggage" baggage
  Promise<String> testPromise() {
    Promise.sync {
      Scope tracerScope = GlobalTracer.get().scopeManager().active()
      return tracerScope.span().getBaggageItem("test-baggage")
    }
  }
}
