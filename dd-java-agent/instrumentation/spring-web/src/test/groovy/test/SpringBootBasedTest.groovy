// Modified by SignalFx
package test

import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.asserts.TraceAssert
import io.opentracing.tag.Tags
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.embedded.LocalServerPort
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.util.NestedServletException

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class SpringBootBasedTest extends AgentTestRunner {

  @LocalServerPort
  private int port

  @Autowired
  private TestRestTemplate restTemplate

  def "valid response"() {
    expect:
    port != 0
    restTemplate.getForObject("http://localhost:$port/", String) == "Hello World"

    and:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          operationName "GET /"
          parent()
          errored false
          tags {
            "http.url" "http://localhost:$port/"
            "http.method" "GET"
            "span.kind" "server"
            "span.origin.type" "org.apache.catalina.core.ApplicationFilterChain"
            "component" "java-web-servlet"
            "http.status_code" 200
            defaultTags()
          }
        }
        controllerSpan(it, 1, "TestController.greeting")
      }
    }
  }

  def "generates spans"() {
    expect:
    restTemplate.getForObject("http://localhost:$port/param/asdf1234/", String) == "Hello asdf1234"

    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          operationName "GET /param/{parameter}/"
          parent()
          errored false
          tags {
            "http.url" "http://localhost:$port/param/asdf1234/"
            "http.method" "GET"
            "span.kind" "server"
            "span.origin.type" "org.apache.catalina.core.ApplicationFilterChain"
            "component" "java-web-servlet"
            "http.status_code" 200
            defaultTags()
          }
        }
        controllerSpan(it, 1, "TestController.withParam")
      }
    }
  }

  def "generates 404 spans"() {
    setup:
    def response = restTemplate.getForObject("http://localhost:$port/invalid", Map)

    expect:
    response.get("status") == 404
    response.get("error") == "Not Found"

    assertTraces(2) {
      trace(0, 2) {
        span(0) {
          operationName "404"
          parent()
          errored false
          tags {
            "http.url" "http://localhost:$port/invalid"
            "http.method" "GET"
            "span.kind" "server"
            "span.origin.type" "org.apache.catalina.core.ApplicationFilterChain"
            "component" "java-web-servlet"
            "http.status_code" 404
            defaultTags()
          }
        }
        controllerSpan(it, 1, "ResourceHttpRequestHandler.handleRequest")
      }
      trace(1, 2) {
        span(0) {
          operationName "404"
          parent()
          errored false
          tags {
            "http.url" "http://localhost:$port/error"
            "http.method" "GET"
            "span.kind" "server"
            "span.origin.type" "org.apache.catalina.core.ApplicationFilterChain"
            "component" "java-web-servlet"
            "http.status_code" 404
            defaultTags()
          }
        }
        controllerSpan(it, 1, "BasicErrorController.error")
      }
    }
  }

  def "generates error spans"() {
    setup:
    def response = restTemplate.getForObject("http://localhost:$port/error/qwerty/", Map)

    expect:
    response.get("status") == 500
    response.get("error") == "Internal Server Error"
    response.get("exception") == "java.lang.RuntimeException"
    response.get("message") == "qwerty"

    assertTraces(2) {
      trace(0, 2) {
        span(0) {
          operationName "GET /error/{parameter}/"
          parent()
          errored true
          tags {
            "http.url" "http://localhost:$port/error/qwerty/"
            "http.method" "GET"
            "span.kind" "server"
            "span.origin.type" "org.apache.catalina.core.ApplicationFilterChain"
            "component" "java-web-servlet"
            "http.status_code" 500
            errorTags NestedServletException, "Request processing failed; nested exception is java.lang.RuntimeException: qwerty"
            defaultTags()
          }
        }
        controllerSpan(it, 1, "TestController.withError", RuntimeException)
      }
      trace(1, 2) {
        span(0) {
          operationName "GET /error"
          parent()
          errored true
          tags {
            "http.url" "http://localhost:$port/error"
            "http.method" "GET"
            "span.kind" "server"
            "span.origin.type" "org.apache.catalina.core.ApplicationFilterChain"
            "component" "java-web-servlet"
            "http.status_code" 500
            "error" true
            defaultTags()
          }
        }
        controllerSpan(it, 1, "BasicErrorController.error")
      }
    }
  }

  def "validated form"() {
    expect:
    restTemplate.postForObject("http://localhost:$port/validated", new TestForm("bob", 20), String) == "Hello bob Person(Name: bob, Age: 20)"

    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          operationName "POST /validated"
          parent()
          errored false
          tags {
            "http.url" "http://localhost:$port/validated"
            "http.method" "POST"
            "span.kind" "server"
            "span.origin.type" "org.apache.catalina.core.ApplicationFilterChain"
            "component" "java-web-servlet"
            "http.status_code" 200
            defaultTags()
          }
        }
        controllerSpan(it, 1, "TestController.withValidation")
      }
    }
  }

  def "invalid form"() {
    setup:
    def response = restTemplate.postForObject("http://localhost:$port/validated", new TestForm("bill", 5), Map, Map)

    expect:
    response.get("status") == 400
    response.get("error") == "Bad Request"
    response.get("exception") == "org.springframework.web.bind.MethodArgumentNotValidException"
    response.get("message") == "Validation failed for object='testForm'. Error count: 1"

    assertTraces(2) {
      trace(0, 2) {
        span(0) {
          operationName "POST /validated"
          parent()
          errored false
          tags {
            "http.url" "http://localhost:$port/validated"
            "http.method" "POST"
            "span.kind" "server"
            "span.origin.type" "org.apache.catalina.core.ApplicationFilterChain"
            "component" "java-web-servlet"
            "http.status_code" 400
            "error" false
            defaultTags()
          }
        }
        controllerSpan(it, 1, "TestController.withValidation", MethodArgumentNotValidException)
      }
      trace(1, 2) {
        span(0) {
          operationName "POST /error"
          parent()
          errored false
          tags {
            "http.url" "http://localhost:$port/error"
            "http.method" "POST"
            "span.kind" "server"
            "span.origin.type" "org.apache.catalina.core.ApplicationFilterChain"
            "component" "java-web-servlet"
            "http.status_code" 400
            defaultTags()
          }
        }
        controllerSpan(it, 1, "BasicErrorController.error")
      }
    }
  }

  def controllerSpan(TraceAssert trace, int index, String name, Class<Throwable> errorType = null) {
    trace.span(index) {
      operationName name
      childOf(trace.span(0))
      errored errorType != null
      tags {
        "$Tags.COMPONENT.key" "spring-web-controller"
        if (errorType) {
          errorTags(errorType)
        }
        defaultTags()
      }
    }
  }
}
