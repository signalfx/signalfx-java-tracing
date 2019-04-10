package test

import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.api.DDSpanTypes
import io.opentracing.tag.Tags
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.boot.web.client.RestTemplateCustomizer
import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.http.converter.StringHttpMessageConverter
import org.springframework.web.client.RestTemplate

import static datadog.trace.agent.test.server.http.TestHttpServer.httpServer
import static datadog.trace.agent.test.utils.TraceUtils.runUnderTrace

class RestTemplateTest extends AgentTestRunner {

  class NoopInterceptor implements ClientHttpRequestInterceptor {

    @Override
    ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
      return execution.execute(request, body)
    }
  }

  class NoopRestTemplateCustomizer implements RestTemplateCustomizer {
    @Override
    void customize(RestTemplate restTemplate) {
    }
  }

  def "sending a request creates spans and sends headers: #clientKind"() {
    setup:
    def server = httpServer {
      handlers {
        all {
          response.status(200).send("pong")
        }
      }
    }
    def client = obj

    def interceptors = (ArrayList<ClientHttpRequestInterceptor>) client.getInterceptors()
    interceptors.add(new NoopInterceptor())
    client.setInterceptors(interceptors)

    def response
    runUnderTrace("parent") {
      response = client.getForEntity("http://localhost:$server.address.port/ping", String)
    }

    expect:
    // Confirm TracingInterceptor functional alongside others
    client.getInterceptors().size() == 2
    client.getInterceptors().get(0) instanceof datadog.trace.instrumentation.springweb.TracingInterceptor
    client.getInterceptors().get(1) instanceof NoopInterceptor

    response.getBody().toString() == "pong"
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          serviceName "unnamed-java-app"
          operationName "parent"
          resourceName "parent"
          spanType null
          parent()
          errored false
          tags {
            defaultTags()
          }
        }
        span(1) {
          operationName "http.request"
          serviceName  "rest-template"
          resourceName "GET /ping"
          errored false
          childOf(span(0))
          tags {
            defaultTags()
            "component" "rest-template"
            "span.type" DDSpanTypes.HTTP_CLIENT
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.HTTP_STATUS.key" 200
            "$Tags.HTTP_URL.key" "http://localhost:$server.address.port/ping"
            "$Tags.PEER_HOSTNAME.key" "localhost"
            "$Tags.PEER_PORT.key" server.address.port
          }
        }
      }
    }

    server.lastRequest.headers.get("x-datadog-trace-id") == TEST_WRITER[0][1].traceId
    server.lastRequest.headers.get("x-datadog-parent-id") == TEST_WRITER[0][1].spanId

    cleanup:
    server.close()

    where:
    clientKind | obj
    "Empty Constructor"     | new RestTemplate()
    "Request Factory"       | new RestTemplate(new SimpleClientHttpRequestFactory())
    "MessageConverter List" | new RestTemplate([new StringHttpMessageConverter()])
    "RestTemplateBuilder"   | new RestTemplateBuilder(new NoopRestTemplateCustomizer()).build()
  }
}
