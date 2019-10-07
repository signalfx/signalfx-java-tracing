// Modified by SignalFx
import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.utils.OkHttpUtils
import datadog.trace.agent.test.utils.PortUtils
import datadog.trace.api.DDSpanTypes
import io.netty.handler.codec.http.HttpResponseStatus
import io.opentracing.tag.Tags
import io.vertx.core.Vertx
import io.vertx.core.Handler
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
      trace(0, 6) {
        span(0) {
          childOf span(2)
          traceId "123"
          serviceName "unnamed-java-app"
          operationName "VertxWebTestServer\$3.handle"
          resourceName "/test"
          spanType DDSpanTypes.HTTP_SERVER
          errored false
          tags {
            "$Tags.COMPONENT.key" "vertx"
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.HTTP_STATUS.key" 200
            "$Tags.HTTP_URL.key" "http://localhost:$port/test"
            "$Tags.PEER_HOSTNAME.key" "localhost"
            "$Tags.PEER_HOST_IPV4.key" "127.0.0.1"
            "$Tags.PEER_PORT.key" Integer
            "handler.type" "io.vertx.ext.web.impl.RoutingContextImpl"
            defaultTags(true)
          }
        }
        span(1) {
          traceId "123"
          parentId "456"
          operationName "netty.request"
          spanType DDSpanTypes.HTTP_SERVER
          tags {
            "$Tags.COMPONENT.key" "netty"
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.HTTP_STATUS.key" 200
            "$Tags.HTTP_URL.key" "http://localhost:$port/test"
            "$Tags.PEER_HOSTNAME.key" "localhost"
            "$Tags.PEER_HOST_IPV4.key" "127.0.0.1"
            "$Tags.PEER_PORT.key" Integer
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_SERVER
            defaultTags()
          }
        }
        span(2) {
          traceId "123"
          childOf span(4)
          operationName "VertxWebTestServer\$4.handle"
          spanType DDSpanTypes.HTTP_SERVER
          tags {
            "$Tags.COMPONENT.key" "vertx"
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.HTTP_STATUS.key" 200
            "$Tags.HTTP_URL.key" "http://localhost:$port/test"
            "$Tags.PEER_HOSTNAME.key" "localhost"
            "$Tags.PEER_HOST_IPV4.key" "127.0.0.1"
            "$Tags.PEER_PORT.key" Integer
            "handler.type" "io.vertx.ext.web.impl.RoutingContextDecorator"
            defaultTags()
          }
        }
        span(3) {
          childOf span(1)
          traceId "123"
          operationName "VertxWebTestServer\$5.handle"
          spanType DDSpanTypes.HTTP_SERVER
          tags {
            "$Tags.COMPONENT.key" "vertx"
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.HTTP_STATUS.key" 200
            "$Tags.HTTP_URL.key" "http://localhost:$port/test"
            "$Tags.PEER_HOSTNAME.key" "localhost"
            "$Tags.PEER_HOST_IPV4.key" "127.0.0.1"
            "$Tags.PEER_PORT.key" Integer
            "handler.type" "io.vertx.ext.web.impl.RoutingContextImpl"
            defaultTags()
          }
        }
        span(4) {
          childOf span(3)
          traceId "123"
          operationName "io.vertx.ext.web.impl.BlockingHandlerDecorator.handle"
          spanType DDSpanTypes.HTTP_SERVER
          tags {
            "$Tags.COMPONENT.key" "vertx"
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.HTTP_STATUS.key" 200
            "$Tags.HTTP_URL.key" "http://localhost:$port/test"
            "$Tags.PEER_HOSTNAME.key" "localhost"
            "$Tags.PEER_HOST_IPV4.key" "127.0.0.1"
            "$Tags.PEER_PORT.key" Integer
            "handler.type" "io.vertx.ext.web.impl.RoutingContextImpl"
            defaultTags()
          }
        }
        span(5) {
          childOf span(3)
          traceId "123"
          operationName "VertxWebTestServer.tracedMethod"
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
          serviceName "unnamed-java-app"
          operationName "netty.request"
          resourceName name
          spanType DDSpanTypes.HTTP_SERVER
          errored error
          tags {
            "$Tags.COMPONENT.key" "netty"
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.HTTP_STATUS.key" responseCode.code()
            "$Tags.HTTP_URL.key" "http://localhost:$port/$path"
            "$Tags.PEER_HOSTNAME.key" "localhost"
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
//    HttpResponseStatus.OK                    | "/"          | ""            | false
    HttpResponseStatus.NOT_FOUND             | "404"        | "doesnt-exit" | false
//    HttpResponseStatus.INTERNAL_SERVER_ERROR | "/error"     | "error"       | true
  }

  def "test OK response handling"() {
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
          serviceName "unnamed-java-app"
          operationName "VertxWebTestServer\$1.handle"
          resourceName name
          spanType DDSpanTypes.HTTP_SERVER
          errored error
          tags {
            "$Tags.COMPONENT.key" "vertx"
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.HTTP_STATUS.key" responseCode.code()
            "$Tags.HTTP_URL.key" "http://localhost:$port/$path"
            "$Tags.PEER_HOSTNAME.key" "localhost"
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
          serviceName "unnamed-java-app"
          operationName "netty.request"
          resourceName name
          spanType DDSpanTypes.HTTP_SERVER
          errored error
          tags {
            "$Tags.COMPONENT.key" "netty"
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.HTTP_STATUS.key" responseCode.code()
            "$Tags.HTTP_URL.key" "http://localhost:$port/$path"
            "$Tags.PEER_HOSTNAME.key" "localhost"
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
//    HttpResponseStatus.NOT_FOUND             | "404"        | "doesnt-exit" | false
//    HttpResponseStatus.INTERNAL_SERVER_ERROR | "/error"     | "error"       | true
  }

  def "test server error response handling"() {
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
          serviceName "unnamed-java-app"
          operationName "VertxWebTestServer\$2.handle"
          resourceName name
          spanType DDSpanTypes.HTTP_SERVER
          errored error
          tags {
            "$Tags.COMPONENT.key" "vertx"
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.HTTP_STATUS.key" responseCode.code()
            "$Tags.HTTP_URL.key" "http://localhost:$port/$path"
            "$Tags.PEER_HOSTNAME.key" "localhost"
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
          serviceName "unnamed-java-app"
          operationName "netty.request"
          resourceName name
          spanType DDSpanTypes.HTTP_SERVER
          errored error
          tags {
            "$Tags.COMPONENT.key" "netty"
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.HTTP_STATUS.key" responseCode.code()
            "$Tags.HTTP_URL.key" "http://localhost:$port/$path"
            "$Tags.PEER_HOSTNAME.key" "localhost"
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
//    HttpResponseStatus.OK                    | "/"          | ""            | false
//    HttpResponseStatus.NOT_FOUND             | "404"        | "doesnt-exit" | false
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
          serviceName "unnamed-java-app"
          operationName "VertxServerTest.handle"
          spanType DDSpanTypes.HTTP_SERVER
          tags {
            "$Tags.COMPONENT.key" "vertx"
            "handler.type" "java.lang.Object"
            defaultTags()
          }
        }
      }
    }
  }
}
