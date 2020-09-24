package test


import com.google.common.collect.ImmutableMap
import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.asserts.ListWriterAssert
import datadog.trace.agent.test.utils.OkHttpUtils
import datadog.trace.agent.test.utils.PortUtils
import datadog.trace.bootstrap.instrumentation.api.Tags
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.springframework.boot.SpringApplication
import org.springframework.context.ConfigurableApplicationContext
import spock.lang.Shared

class CamelSpringBootBasedTest extends AgentTestRunner {

  @Shared
  ConfigurableApplicationContext server
  @Shared
  OkHttpClient client = OkHttpUtils.client()
  @Shared
  int port = PortUtils.randomOpenPort()
  @Shared
  URI address = new URI("http://localhost:$port/")


  def setupSpec() {
    def app = new SpringApplication(AppConfig)
    app.setDefaultProperties(ImmutableMap.of("camelService.port", port))
    return app.run()
  }

  def cleanupSpec() {
    if (server != null) {
      server.close()
      server = null
    }
  }

  def "single camel service span"() {
    setup:
    def requestUrl = address.resolve("/camelService")
    def url = HttpUrl.get(requestUrl)
    def request = new Request.Builder()
      .url(url)
      .method("POST",
        new FormBody.Builder().add("", "testContent").build())
      .build()

    when:
    client.newCall(request).execute()

    then:
    cleanAndAssertTraces(1) {
      trace(0, 1) {
        it.span(0) {
          serviceName "camel-sample-SERVICE-1"
          operationName "POST"
          resourceName "/camelService"
          tags {
            "$Tags.COMPONENT" "camel-http"
            "$Tags.HTTP_METHOD" "POST"
            "$Tags.HTTP_URL" "${address.resolve("/camelService")}"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_SERVER
            "camel.uri" "${address.resolve("/camelService")}".replace("localhost", "0.0.0.0")

            defaultTags(true)
          }
        }
      }
    }
  }

  void cleanAndAssertTraces(
    final int size,
    @ClosureParams(value = SimpleType, options = "datadog.trace.agent.test.asserts.ListWriterAssert")
    @DelegatesTo(value = ListWriterAssert, strategy = Closure.DELEGATE_FIRST)
    final Closure spec) {

    TEST_WRITER.waitForTraces(size)
    assertTraces(size, spec)
  }
}
