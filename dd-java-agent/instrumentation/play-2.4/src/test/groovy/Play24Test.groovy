// Modified by SignalFx
import datadog.trace.agent.test.utils.TestSpan
import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.TestUtils
import datadog.trace.agent.test.utils.OkHttpUtils
import datadog.trace.api.DDSpanTypes
import okhttp3.Request
import play.api.test.TestServer
import play.test.Helpers
import spock.lang.Shared

class Play24Test extends AgentTestRunner {
  @Shared
  int port
  @Shared
  TestServer testServer

  @Shared
  def client = OkHttpUtils.client()

  def setupSpec() {
    port = TestUtils.randomOpenPort()
    testServer = Helpers.testServer(port, Play24TestUtils.buildTestApp())
    testServer.start()
  }

  def cleanupSpec() {
    testServer.stop()
  }

  def "request traces"() {
    setup:
    def request = new Request.Builder()
      .url("http://localhost:$port/helloplay/spock")
      .header("traceid", "123")
      .header("spanid", "0")
      .get()
      .build()
    def response = client.newCall(request).execute()
    TEST_WRITER.waitForTraces(1)
    TestSpan[] playTrace = TEST_WRITER.get(0)
    TestSpan root = playTrace[0]

    expect:
    testServer != null
    response.code() == 200
    response.body().string() == "hello spock"

    assertTraces(1) {
      trace(0, 2) {
        span(1) {
          operationName 'TracedWork$.doWork'
        }
        span(0) {
          traceId 123
          parentId 0
          operationName "GET /helloplay/:from"
          errored false
          tags {
            "http.status_code" 200
            "http.url" "/helloplay/:from"
            "http.method" "GET"
            "span.kind" "server"
            "component" "play-action"
          }
        }
      }
    }
  }

  def "5xx errors trace"() {
    setup:
    def request = new Request.Builder()
      .url("http://localhost:$port/make-error")
      .get()
      .build()
    def response = client.newCall(request).execute()
    TEST_WRITER.waitForTraces(1)
    TestSpan[] playTrace = TEST_WRITER.get(0)
    TestSpan root = playTrace[0]

    expect:
    testServer != null
    response.code() == 500

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          operationName "GET /make-error"
          errored true
          tags {
            "http.status_code" 500
            "http.url" "/make-error"
            "http.method" "GET"
            "span.kind" "server"
            "component" "play-action"
            "error" true
          }
        }
      }
    }
  }

  def "error thrown in request"() {
    setup:
    def request = new Request.Builder()
      .url("http://localhost:$port/exception")
      .get()
      .build()
    def response = client.newCall(request).execute()
    TEST_WRITER.waitForTraces(1)
    TestSpan[] playTrace = TEST_WRITER.get(0)
    TestSpan root = playTrace[0]

    expect:
    testServer != null
    response.code() == 500

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          errored true
          operationName "GET /exception"
          tags {
            "http.status_code" 500
            "http.url" "/exception"
            "http.method" "GET"
            "span.kind" "server"
            "component" "play-action"
            "error" true
          }
        }
      }
    }
  }

  def "4xx errors trace"() {
    setup:
    def request = new Request.Builder()
      .url("http://localhost:$port/nowhere")
      .get()
      .build()
    def response = client.newCall(request).execute()
    TEST_WRITER.waitForTraces(1)
    TestSpan[] playTrace = TEST_WRITER.get(0)
    TestSpan root = playTrace[0]

    expect:
    testServer != null
    response.code() == 404

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          errored false
          operationName "GET /nowhere"
          tags {
            "http.status_code" 404
            "http.url" "/nowhere"
            "http.method" "GET"
            "span.kind" "server"
            "component" "play-action"
          }
        }
      }
    }
  }
}
