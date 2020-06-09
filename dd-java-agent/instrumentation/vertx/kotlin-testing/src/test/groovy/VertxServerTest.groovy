// Modified by SignalFx
import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.utils.OkHttpUtils
import datadog.trace.agent.test.utils.PortUtils
import datadog.trace.api.DDSpanTypes
import datadog.trace.bootstrap.instrumentation.api.Tags
import io.netty.handler.codec.http.HttpResponseStatus
import io.vertx.core.Vertx
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
      trace(0, 2) {
        span(0) {
          traceId 123G
          parentId 456G
          serviceName "unnamed-java-service"
          operationName "netty.request"
          resourceName "/test"
          spanType DDSpanTypes.HTTP_SERVER
          errored false
          tags {
            "$Tags.COMPONENT" "netty"
            "$Tags.HTTP_METHOD" "GET"
            "$Tags.HTTP_STATUS" 200
            "$Tags.HTTP_URL" "http://localhost:$port/test"
            "$Tags.PEER_HOST_IPV4" "127.0.0.1"
            "$Tags.PEER_PORT" Integer
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_SERVER
            defaultTags(true)
          }
        }
        span(1) {
          childOf span(0)
          assert span(1).operationName.endsWith('.tracedMethod')
        }
      }
    }
  }

  def "test #responseCode response handling"() {
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
            "$Tags.COMPONENT" "netty"
            "$Tags.HTTP_METHOD" "GET"
            "$Tags.HTTP_STATUS" responseCode.code()
            "$Tags.HTTP_URL" "http://localhost:$port/$path"
            "$Tags.PEER_HOST_IPV4" "127.0.0.1"
            "$Tags.PEER_PORT" Integer
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_SERVER
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
    HttpResponseStatus.NOT_FOUND             | "404"        | "doesnt-exit" | false
    HttpResponseStatus.INTERNAL_SERVER_ERROR | "/error"     | "error"       | true
  }
}
