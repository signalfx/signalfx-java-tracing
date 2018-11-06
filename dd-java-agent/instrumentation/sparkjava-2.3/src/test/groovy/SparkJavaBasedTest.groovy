import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.TestUtils
import datadog.trace.agent.test.utils.OkHttpUtils
import okhttp3.OkHttpClient
import okhttp3.Request
import spark.Spark
import spark.embeddedserver.jetty.JettyHandler
import spock.lang.Shared

class SparkJavaBasedTest extends AgentTestRunner {

  static {
    System.setProperty("dd.integration.jetty.enabled", "true")
    System.setProperty("dd.integration.sparkjava.enabled", "true")
  }

  @Shared
  int port

  OkHttpClient client = OkHttpUtils.client()

  def setupSpec() {
    port = TestUtils.randomOpenPort()
    TestSparkJavaApplication.initSpark(port)
  }

  def cleanupSpec() {
    Spark.stop()
  }

  def "valid response"() {
    setup:
    def request = new Request.Builder()
      .url("http://localhost:$port/")
      .get()
      .build()
    def response = client.newCall(request).execute()

    expect:
    port != 0
    response.body().string() == "Hello World"
  }

  def "valid response with registered trace"() {
    setup:
    def request = new Request.Builder()
      .url("http://localhost:$port/")
      .get()
      .build()
    def response = client.newCall(request).execute()

    expect:
    port != 0
    response.body().string() == "Hello World"

    and:
    TEST_WRITER.waitForTraces(1)
    TEST_WRITER.size() == 1
  }


  def "generates spans"() {
    setup:
    def request = new Request.Builder()
      .url("http://localhost:$port/param/asdf1234")
      .get()
      .build()
    def response = client.newCall(request).execute()

    expect:
    response.body().string() == "Hello asdf1234"

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          operationName "GET /param/:param"
          errored false
          parent()
          tags {
            "http.url" "http://localhost:$port/param/asdf1234"
            "http.method" "GET"
            "span.kind" "server"
            "component" "jetty-handler"
            "http.status_code" 200
            "span.origin.type" JettyHandler.name
            "servlet.context" null
          }
        }
      }
    }

  }
}
