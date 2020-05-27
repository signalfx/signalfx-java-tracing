// Modified by SignalFx
import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.utils.OkHttpUtils
import datadog.trace.agent.test.utils.PortUtils
import datadog.trace.api.DDSpanTypes
import io.netty.handler.codec.http.HttpResponseStatus
import io.opentracing.tag.Tags
import io.vertx.core.Vertx
import io.vertx.core.Handler
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import spock.lang.Shared

class VertxServerTest extends AgentTestRunner {

  @Shared
  OkHttpClient client = OkHttpUtils.client()

  @Shared
  int port
  @Shared
  Vertx server

  def setupSpec() {
    port = PortUtils.randomOpenPort()
    server = VertxWebTestServer.start(port)
  }

  def cleanupSpec() {
    server.close()
  }

  def "test server request/response"() {
    setup:
    def request = new Request.Builder()
      .url("http://localhost:$port/test")
      .header("x-b3-traceid", "7b")
      .header("x-b3-spanid", "1c8")
      .get()
      .build()
    def response = client.newCall(request).execute()

    expect:
    response.code() == 200
    response.body().string() == "Hello World"

    and:
    assertTraces(1) {
      trace(0, 7) {
        span(0) {
          childOf span(2)
          traceId 123G
          serviceName "unnamed-java-service"
          operationName "VertxWebTestServer\$\$Lambda.handle"
          resourceName "VertxWebTestServer\$\$Lambda.handle"
          errored false
          tags {
            "$Tags.COMPONENT.key" "vertx"
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.HTTP_STATUS.key" 200
            "$Tags.HTTP_URL.key" "http://localhost:$port/test"
            "$Tags.PEER_HOST_IPV4.key" "127.0.0.1"
            "$Tags.PEER_PORT.key" Integer
            "handler.type" "io.vertx.ext.web.impl.RoutingContextImpl"
            defaultTags()
          }
        }
        span(1) {
          traceId 123G
          parentId 456G
          operationName "netty.request"
          resourceName "/test"
          spanType DDSpanTypes.HTTP_SERVER
          tags {
            "$Tags.COMPONENT.key" "netty"
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.HTTP_STATUS.key" 200
            "$Tags.HTTP_URL.key" "http://localhost:$port/test"
            "$Tags.PEER_HOST_IPV4.key" "127.0.0.1"
            "$Tags.PEER_PORT.key" Integer
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_SERVER
            defaultTags(true)
          }
        }
        span(2) {
          traceId 123G
          childOf span(5)
          operationName "VertxWebTestServer\$MyHandler.handle"
          resourceName "VertxWebTestServer\$MyHandler.handle"
          tags {
            "$Tags.COMPONENT.key" "vertx"
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.HTTP_STATUS.key" 200
            "$Tags.HTTP_URL.key" "http://localhost:$port/test"
            "$Tags.PEER_HOST_IPV4.key" "127.0.0.1"
            "$Tags.PEER_PORT.key" Integer
            "handler.type" "io.vertx.ext.web.impl.RoutingContextDecorator"
            defaultTags()
          }
        }
        span(3) {
          childOf span(2)
          traceId 123G
          operationName "VertxWebTestServer.tracedMethod"
          resourceName "VertxWebTestServer.tracedMethod"
          tags {
            "$Tags.COMPONENT.key" "trace"
            defaultTags()
          }
        }
        span(4) {
          childOf span(1)
          traceId 123G
          operationName "VertxWebTestServer\$\$Lambda.handle"
          tags {
            "$Tags.COMPONENT.key" "vertx"
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.HTTP_STATUS.key" 200
            "$Tags.HTTP_URL.key" "http://localhost:$port/test"
            "$Tags.PEER_HOST_IPV4.key" "127.0.0.1"
            "$Tags.PEER_PORT.key" Integer
            "handler.type" "io.vertx.ext.web.impl.RoutingContextImpl"
            defaultTags()
          }
        }
        span(5) {
          childOf span(4)
          traceId 123G
          operationName "io.vertx.ext.web.impl.BlockingHandlerDecorator.handle"
          resourceName "io.vertx.ext.web.impl.BlockingHandlerDecorator.handle"
          tags {
            "$Tags.COMPONENT.key" "vertx"
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.HTTP_STATUS.key" 200
            "$Tags.HTTP_URL.key" "http://localhost:$port/test"
            "$Tags.PEER_HOST_IPV4.key" "127.0.0.1"
            "$Tags.PEER_PORT.key" Integer
            "handler.type" "io.vertx.ext.web.impl.RoutingContextImpl"
            defaultTags()
          }
        }
        span(6) {
          childOf span(4)
          traceId 123G
          operationName "VertxWebTestServer.tracedMethod"
          resourceName "VertxWebTestServer.tracedMethod"
          tags {
            "$Tags.COMPONENT.key" "trace"
            defaultTags()
          }
        }
      }
    }
  }

  def "test notFound response handling"() {
    setup:
    def request = new Request.Builder().url("http://localhost:$port/$path").get().build()
    def response = client.newCall(request).execute()

    expect:
    response.code() == responseCode.code()

    and:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          serviceName "unnamed-java-service"
          operationName "netty.request"
          resourceName name
          spanType DDSpanTypes.HTTP_SERVER
          errored error
          tags {
            "$Tags.COMPONENT.key" "netty"
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.HTTP_STATUS.key" responseCode.code()
            "$Tags.HTTP_URL.key" "http://localhost:$port/$path"
            "$Tags.PEER_HOST_IPV4.key" "127.0.0.1"
            "$Tags.PEER_PORT.key" Integer
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_SERVER
            if (error) {
              tag("error", true)
            }
            defaultTags()
          }
        }
      }
    }

    where:
    responseCode                             | name         | path          | error
    HttpResponseStatus.NOT_FOUND             | "404"        | "doesnt-exist" | false
  }

  def "test #responseCode response handling"() {
    setup:
    def request = new Request.Builder().url("http://localhost:$port/$path").get().build()
    def response = client.newCall(request).execute()

    expect:
    response.code() == responseCode.code()

    and:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          childOf span(1)
          serviceName "unnamed-java-service"
          operationName "VertxWebTestServer\$\$Lambda.handle"
          resourceName  "VertxWebTestServer\$\$Lambda.handle"
          errored error
          tags {
            "$Tags.COMPONENT.key" "vertx"
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.HTTP_STATUS.key" responseCode.code()
            "$Tags.HTTP_URL.key" "http://localhost:$port/$path"
            "$Tags.PEER_HOST_IPV4.key" "127.0.0.1"
            "$Tags.PEER_PORT.key" Integer
            "handler.type" "io.vertx.ext.web.impl.RoutingContextImpl"

            if (error) {
              tag("error", true)
            }
            defaultTags()
          }
        }
        span(1) {
          serviceName "unnamed-java-service"
          operationName "netty.request"
          resourceName name
          spanType DDSpanTypes.HTTP_SERVER
          errored error
          tags {
            "$Tags.COMPONENT.key" "netty"
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.HTTP_STATUS.key" responseCode.code()
            "$Tags.HTTP_URL.key" "http://localhost:$port/$path"
            "$Tags.PEER_HOST_IPV4.key" "127.0.0.1"
            "$Tags.PEER_PORT.key" Integer
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_SERVER
            if (error) {
              tag("error", true)
            }
            defaultTags()
          }
        }
      }
    }

    where:
    responseCode                             | name         | path          | error
    HttpResponseStatus.OK                    | "/"          | ""            | false
    HttpResponseStatus.INTERNAL_SERVER_ERROR | "/error"     | "error"       | true
  }

  def "generic handler adds a span"() {
    setup:
    Handler<Object> handler = new Handler<Object>() {
      void handle(Object event) { }
    }

    when:
    handler.handle(new Object())

    then:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          serviceName "unnamed-java-service"
          operationName "VertxServerTest.handle"
          resourceName "VertxServerTest.handle"
          tags {
            "$Tags.COMPONENT.key" "vertx"
            "handler.type" "java.lang.Object"
            defaultTags()
          }
        }
      }
    }
  }

  def "test post"() {
    setup:
    def requestBody = new MultipartBody.Builder()
      .setType(MultipartBody.FORM)
      .addFormDataPart("test", "Testing post")
      .build()

    def request = new Request.Builder()
      .url("http://localhost:$port/test/post")
      .header("x-b3-traceid", "7b")
      .header("x-b3-spanid", "1c8")
      .post(requestBody)
      .build()

    def response = client.newCall(request).execute()

    expect:
    response.code() == 201
    response.body().string() == "Testing post"

    and:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          childOf span(1)
          traceId 123G
          serviceName "unnamed-java-service"
          operationName "VertxWebTestServer\$\$Lambda.handle"
          resourceName "VertxWebTestServer\$\$Lambda.handle"
          errored false
          tags {
            "$Tags.COMPONENT.key" "vertx"
            "$Tags.HTTP_METHOD.key" "POST"
            "$Tags.HTTP_STATUS.key" 201
            "$Tags.HTTP_URL.key" "http://localhost:$port/test/post"
            "$Tags.PEER_HOST_IPV4.key" "127.0.0.1"
            "$Tags.PEER_PORT.key" Integer
            "handler.type" "io.vertx.ext.web.impl.RoutingContextImpl"
            defaultTags()
          }
        }
        span(1) {
          traceId 123G
          parentId 456G
          serviceName "unnamed-java-service"
          operationName "netty.request"
          resourceName "/test/post"
          spanType DDSpanTypes.HTTP_SERVER
          errored false
          tags {
            "$Tags.COMPONENT.key" "netty"
            "$Tags.HTTP_METHOD.key" "POST"
            "$Tags.HTTP_STATUS.key" 201
            "$Tags.HTTP_URL.key" "http://localhost:$port/test/post"
            "$Tags.PEER_HOST_IPV4.key" "127.0.0.1"
            "$Tags.PEER_PORT.key" Integer
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_SERVER
            defaultTags(true)
          }
        }
      }
    }
  }
}
