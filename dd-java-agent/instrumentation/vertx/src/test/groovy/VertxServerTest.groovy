// Modified by SignalFx
import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.TestUtils
import datadog.trace.agent.test.utils.OkHttpUtils
import datadog.trace.api.DDSpanTypes
import datadog.trace.api.DDTags
import io.netty.handler.codec.http.HttpResponseStatus
import io.opentracing.tag.Tags
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
    port = TestUtils.randomOpenPort()
    server = VertxWebTestServer.start(port)
  }

  def cleanupSpec() {
    server.close()
  }

  def "test server request/response"() {
    setup:
    def request = new Request.Builder()
      .url("http://localhost:$port/test")
      .header("traceid", "123")
      .header("spanid", "0")
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
          traceId 123
          parentId 0
          operationName "GET /test"
          errored false
          tags {
            "$Tags.COMPONENT.key" "netty"
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.HTTP_STATUS.key" 200
            "$Tags.HTTP_URL.key" "http://localhost:$port/test"
            "$Tags.PEER_HOSTNAME.key" "localhost"
            "$Tags.PEER_PORT.key" Integer
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_SERVER
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
          operationName name
          errored error
          tags {
            "$Tags.COMPONENT.key" "netty"
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.HTTP_STATUS.key" responseCode.code()
            "$Tags.HTTP_URL.key" "http://localhost:$port/$path"
            "$Tags.PEER_HOSTNAME.key" "localhost"
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
    responseCode                             | name               | path           | error
    HttpResponseStatus.OK                    | "GET /"            | ""            | false
    HttpResponseStatus.NOT_FOUND             | "GET /doesnt-exit" | "doesnt-exit" | false
    HttpResponseStatus.INTERNAL_SERVER_ERROR | "GET /error"       | "error"       | true
  }
}
