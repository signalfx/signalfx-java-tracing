// Modified by SignalFx
import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.api.DDSpanTypes
import io.opentracing.tag.Tags
import spock.lang.AutoCleanup
import spock.lang.Shared

import static datadog.trace.agent.test.server.http.TestHttpServer.httpServer
import static datadog.trace.agent.test.utils.TraceUtils.runUnderTrace
import khttp.KHttp

class KHttpClientTest extends AgentTestRunner {

  static final RESPONSE = "<html><body><h1>Hello test.</h1>"
  static final STATUS = 202

  @AutoCleanup
  @Shared
  def server = httpServer {
    handlers {
      get("/get") {
        handleDistributedRequest()
        response.status(STATUS).send(RESPONSE)
      }
      post("/post") {
        handleDistributedRequest()
        def body = request.headers.get("x-some-header")
        response.status(STATUS).send(body)
      }
    }
  }

  def "test get request"() {
    setup:
    def address = server.address.toURL().toString() + "/get"

    runUnderTrace("someTrace") {
      def r = KHttp.get(address.toString()) // Uses default EmptyMap header arg
      assert r.getStatusCode() == STATUS
      assert r.getText() == RESPONSE
    }

    expect:
    assert true
    assertTraces(2) {
      server.distributedRequestTrace(it, 0, TEST_WRITER[1][1])
      trace(1, 2) {
        span(0) {
          operationName "someTrace"
          parent()
          errored false
          tags {
            defaultTags()
          }
        }
        span(1) {
          serviceName "unnamed-java-service"
          operationName "http.request"
          resourceName "http.request"
          spanType DDSpanTypes.HTTP_CLIENT
          childOf span(0)
          errored false
          tags {
            "$Tags.COMPONENT.key" "khttp"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$Tags.HTTP_URL.key" address
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.HTTP_STATUS.key" STATUS
            "$Tags.PEER_HOSTNAME.key" "localhost"
            "$Tags.PEER_PORT.key" server.address.port
            defaultTags()
          }
        }
      }
    }
  }

  def "test post request"() {
    setup:
    def address = server.address.toURL().toString() + "/post"
    runUnderTrace("someTrace") {
      def urlParameters = ["q":"ASDF", "w":"", "e":"", "r":"12345", "t":""]

      def r = KHttp.post(address.toString(), ["x-some-header":"some-header-value"], urlParameters, "SomeData")
      assert r.getStatusCode() == STATUS
      assert r.getText() == 'some-header-value'
    }

    expect:
    assertTraces(2) {
      server.distributedRequestTrace(it, 0, TEST_WRITER[1][1])
      trace(1, 2) {
        span(0) {
          operationName "someTrace"
          parent()
          errored false
          tags {
            defaultTags()
          }
        }
        span(1) {
          serviceName "unnamed-java-service"
          operationName "http.request"
          resourceName "http.request"
          spanType DDSpanTypes.HTTP_CLIENT
          childOf span(0)
          errored false
          tags {
            "$Tags.COMPONENT.key" "khttp"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$Tags.HTTP_URL.key" address
            "$Tags.HTTP_METHOD.key" "POST"
            "$Tags.HTTP_STATUS.key" STATUS
            "$Tags.PEER_HOSTNAME.key" "localhost"
            "$Tags.PEER_PORT.key" server.address.port
            defaultTags()
          }
        }
      }
    }
  }
}
