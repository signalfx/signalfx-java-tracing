// Modified by SignalFx
package test

import com.google.common.collect.ImmutableMap
import datadog.opentracing.DDSpan
import datadog.trace.agent.test.asserts.ListWriterAssert
import datadog.trace.agent.test.asserts.SpanAssert
import datadog.trace.agent.test.asserts.TraceAssert
import datadog.trace.agent.test.base.HttpServerTest
import datadog.trace.api.DDSpanTypes
import datadog.trace.instrumentation.api.Tags
import datadog.trace.instrumentation.servlet3.Servlet3Decorator
import datadog.trace.instrumentation.springweb.SpringWebHttpServerDecorator
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import org.apache.catalina.core.ApplicationFilterChain
import org.springframework.boot.SpringApplication
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.web.servlet.view.RedirectView

import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.CLIENT_EXCEPTION
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.EXCEPTION
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.SUCCESS
import static java.util.Collections.singletonMap

class SpringBootBasedTest extends HttpServerTest<ConfigurableApplicationContext, Servlet3Decorator> {

  def "allowedExceptions=IllegalArgument should tag error=#error when exception=#exception is thrown"() {
    setup:
    def method = "GET"
    def request = request(CLIENT_EXCEPTION, method, null,
      ImmutableMap.of("exceptionName", exception.getName())).build()
    def response = client.newCall(request).execute()

    expect:
    response.code() == (error ? EXCEPTION.status : CLIENT_EXCEPTION.status)
    if (testExceptionBody()) {
      assert response.body().string() == CLIENT_EXCEPTION.getBody().toString()
    }

    and:
    cleanAndAssertTraces(1) {
      trace(0, 2) {
        serverSpan(it, 0, null, null, method, CLIENT_EXCEPTION, error)
        handlerSpan(it, 1, span(0), CLIENT_EXCEPTION, error)
      }
    }

    where:
    exception                | error
    IllegalArgumentException | false
    Exception                | true
  }

  @Override
  ConfigurableApplicationContext startServer(int port) {
    def app = new SpringApplication(AppConfig)
    app.setDefaultProperties(singletonMap("server.port", port))
    def context = app.run()
    return context
  }

  @Override
  void stopServer(ConfigurableApplicationContext ctx) {
    ctx.close()
  }

  @Override
  Servlet3Decorator decorator() {
    return Servlet3Decorator.DECORATE
  }

  @Override
  String expectedOperationName() {
    return "servlet.request"
  }

  @Override
  boolean hasHandlerSpan() {
    true
  }

  @Override
  boolean testNotFound() {
    // FIXME: the instrumentation adds an extra controller span which is not consistent.
    // Fix tests or remove extra span.
    false
  }

  void cleanAndAssertTraces(
    final int size,
    @ClosureParams(value = SimpleType, options = "datadog.trace.agent.test.asserts.ListWriterAssert")
    @DelegatesTo(value = ListWriterAssert, strategy = Closure.DELEGATE_FIRST)
    final Closure spec) {

    // If this is failing, make sure HttpServerTestAdvice is applied correctly.
    TEST_WRITER.waitForTraces(size * 2)

    TEST_WRITER.each {
      def renderSpan = it.find {
        it.operationName == "response.render"
      }
      if (renderSpan) {
        SpanAssert.assertSpan(renderSpan) {
          operationName "response.render"
          resourceName "response.render"
          spanType "web"
          errored false
          tags {
            "component" "spring-webmvc"
            "view.type" RedirectView.name
            "span.kind" "server"
            defaultTags()
          }
        }
        it.remove(renderSpan)
      }
    }

    super.cleanAndAssertTraces(size, spec)
  }

  @Override
  void handlerSpan(TraceAssert trace, int index, Object parent, ServerEndpoint endpoint = SUCCESS,
                   Boolean error = null) {
    def spanErrored = error != null ? error : endpoint == EXCEPTION
    trace.span(index) {
      serviceName expectedServiceName()
      operationName "spring.handler"
      resourceName "TestController.${endpoint.name().toLowerCase()}"
      spanType DDSpanTypes.HTTP_SERVER
      errored spanErrored
      childOf(parent as DDSpan)
      tags {
        "$Tags.COMPONENT" SpringWebHttpServerDecorator.DECORATE.component()
        "$Tags.SPAN_KIND" Tags.SPAN_KIND_SERVER
        defaultTags()
        if (spanErrored) {
          errorTags(Exception, endpoint.body)
        }
      }
    }
  }

  @Override
  void serverSpan(TraceAssert trace, int index, String traceID = null, String parentID = null,
                  String method = "GET", ServerEndpoint endpoint = SUCCESS, Boolean error = null
  ) {
    def spanErrored = error != null ? error : endpoint.errored
    trace.span(index) {
      serviceName expectedServiceName()
      operationName expectedOperationName()
      resourceName endpoint.status == 404 ? "404" : "${endpoint.resolve(address).path}"
      spanType DDSpanTypes.HTTP_SERVER
      errored spanErrored
      if (parentID != null) {
        traceId traceID
        parentId parentID
      } else {
        parent()
      }
      tags {
        "span.origin.type" ApplicationFilterChain.name

        defaultTags(true)
        "$Tags.COMPONENT" serverDecorator.component()
        if (spanErrored) {
          "$Tags.ERROR" true
          "sfx.error.message" { it == null || it == endpoint.body }
          "sfx.error.object" { it == null || it == Exception.name }
          "sfx.error.kind" { it == null || it instanceof String }
          "sfx.error.stack" { it == null || it instanceof String }
        } else if (endpoint == CLIENT_EXCEPTION) {
          "$Tags.ERROR" false
          "sfx.error.message" CLIENT_EXCEPTION.body
          "sfx.error.object" IllegalArgumentException.name
          "sfx.error.kind" { it instanceof String }
          "sfx.error.stack" { it instanceof String }
        }

        "$Tags.HTTP_STATUS" spanErrored ? EXCEPTION.status : endpoint.status
        "$Tags.HTTP_URL" "${endpoint.resolve(address)}"
        "$Tags.PEER_HOSTNAME" { it == "localhost" || it == "127.0.0.1" }
        "$Tags.PEER_PORT" Integer
        "$Tags.PEER_HOST_IPV4" { it == null || it == "127.0.0.1" } // Optional
        "$Tags.HTTP_METHOD" method
        "$Tags.SPAN_KIND" Tags.SPAN_KIND_SERVER
      }
    }
  }
}
