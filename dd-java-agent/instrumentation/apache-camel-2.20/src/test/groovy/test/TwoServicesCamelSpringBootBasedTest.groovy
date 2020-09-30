package test

import com.google.common.collect.ImmutableMap
import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.asserts.ListWriterAssert
import datadog.trace.agent.test.utils.OkHttpUtils
import datadog.trace.agent.test.utils.PortUtils
import datadog.trace.bootstrap.instrumentation.api.Tags
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import okhttp3.OkHttpClient
import org.apache.camel.CamelContext
import org.apache.camel.ProducerTemplate
import org.apache.camel.builder.RouteBuilder
import org.apache.camel.impl.DefaultCamelContext
import org.springframework.boot.SpringApplication
import org.springframework.context.ConfigurableApplicationContext
import spock.lang.Shared

class TwoServicesCamelSpringBootBasedTest extends AgentTestRunner {

  @Shared
  ConfigurableApplicationContext server
  @Shared
  OkHttpClient client = OkHttpUtils.client()
  @Shared
  int portOne = PortUtils.randomOpenPort()
  @Shared
  int portTwo = PortUtils.randomOpenPort()
  @Shared
  URI address = new URI("http://localhost:$portOne/")

  @Shared
  CamelContext clientContext

  def setupSpec() {
    createServer()
    createClient()
  }

  def createServer() {
    def app = new SpringApplication(TwoServicesConfig)
    app.setDefaultProperties(ImmutableMap.of("service.one.port", portOne, "service.two.port", portTwo))
    server = app.run()
  }

  def createClient() {
    clientContext = new DefaultCamelContext()
    clientContext.addRoutes(new RouteBuilder() {
      void configure() {
        from("direct:input")
          .log("SENT Client request")
          .to("http://localhost:$portOne/serviceOne")
          .log("RECEIVED Client response")
      }
    })
    clientContext.start()
  }

  def cleanupSpec() {
    if (server != null) {
      server.close()
      server = null
    }
  }

  def "two camel service spans"() {
    setup:
    ProducerTemplate template = clientContext.createProducerTemplate()

    when:

    template.sendBody("direct:input", "Example request")

    then:
    cleanAndAssertTraces(1) {
      trace(0, 10) {
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
