//Modified by SignalFx
package test

import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.asserts.TraceAssert
import datadog.trace.api.DDSpanTypes
import datadog.trace.api.DDTags
import io.opentracing.tag.Tags
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.http.HttpStatus
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.util.NestedServletException

import static test.Application.PASS
import static test.Application.USER

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class SpringBootBasedTest extends AgentTestRunner {

  @LocalServerPort
  private int port

  @Autowired
  private Application.RestTemplateTestClient restTemplate

  def "valid response"() {
    expect:
    port != 0
    restTemplate.withBasicAuth(USER, PASS)
      .getForObject("http://localhost:$port/", String) == "Hello World"

    and:
    waitAndSortTraces(2)
    assertTraces(2) {
      trace(0, 2) {
        span(0) {
          operationName "servlet.request"
          resourceName "GET /"
          spanType DDSpanTypes.HTTP_SERVER
          childOf trace(1).get(0)
          errored false
          tags {
            "http.url" "http://localhost:$port/"
            "http.method" "GET"
            "peer.hostname" "127.0.0.1"
            "peer.ipv4" "127.0.0.1"
            "peer.port" Integer
            "span.kind" "server"
            "span.origin.type" "org.apache.catalina.core.ApplicationFilterChain"
            "component" "java-web-servlet"
            "http.status_code" 200
            "$DDTags.USER_NAME" test.Application.USER
            defaultTags(true)
          }
        }
        controllerSpan(it, 1, "TestController.greeting")
      }
      trace(1, 1) {
        span(0) {
          operationName "http.request"
          resourceName "GET /"
          spanType DDSpanTypes.HTTP_CLIENT
          parent()
          errored false
          tags {
            "http.url" "http://localhost:$port/"
            "http.method" "GET"
            "span.kind" "client"
            "component" "rest-template"
            "http.status_code" 200
            "peer.hostname" "localhost"
            "peer.port" port
            defaultTags()
          }
        }
      }
    }
  }

  def "generates spans"() {
    setup:
    def entity = restTemplate.withBasicAuth(USER, PASS)
      .getForEntity("http://localhost:$port/param/$param/", String)

    expect:
    entity.statusCode == status
    if (entity.hasBody()) {
      entity.body == "Hello asdf1234"
    }

    waitAndSortTraces(2)
    assertTraces(2) {
      trace(0, 2) {
        span(0) {
          operationName "servlet.request"
          resourceName(status.value == 404 ? "404" : "GET /param/{parameter}/")
          spanType DDSpanTypes.HTTP_SERVER
          childOf trace(1).get(0)
          errored false
          tags {
            "http.url" "http://localhost:$port/param/$param/"
            "http.method" "GET"
            "peer.hostname" "127.0.0.1"
            "peer.ipv4" "127.0.0.1"
            "peer.port" Integer
            "span.kind" "server"
            "span.origin.type" "org.apache.catalina.core.ApplicationFilterChain"
            "component" "java-web-servlet"
            "http.status_code" status.value
            "$DDTags.USER_NAME" test.Application.USER
            defaultTags(true)
          }
        }
        controllerSpan(it, 1, "TestController.withParam")
      }
      trace(1, 1) {
        span(0) {
          operationName "http.request"
          if (status == HttpStatus.OK) {
            resourceName("GET /param/?/")
          } else {
            resourceName("404")
          }
          spanType DDSpanTypes.HTTP_CLIENT
          parent()
          errored false
          tags {
            "http.url" "http://localhost:$port/param/$param/"
            "http.method" "GET"
            "span.kind" "client"
            "component" "rest-template"
            "http.status_code" status.value
            "peer.hostname" "localhost"
            "peer.port" port
            defaultTags()
          }
        }
      }
    }

    where:
    param      | status
    "asdf1234" | HttpStatus.OK
    "missing"  | HttpStatus.NOT_FOUND
  }

  def "missing auth"() {
    setup:
    def resp = restTemplate.withoutBasicAuth().getForObject("http://localhost:$port/param/asdf1234/", Map)

    expect:
    resp["status"] == 401
    resp["error"] == "Unauthorized"

    waitAndSortTraces(3)
    assertTraces(3) {
      trace(0, 2) {
        span(0) {
          operationName "servlet.request"
          resourceName "GET /error"
          spanType DDSpanTypes.HTTP_SERVER
          childOf(trace(2).get(0))
          errored false
          tags {
            "http.url" "http://localhost:$port/error"
            "http.method" "GET"
            "peer.hostname" "127.0.0.1"
            "peer.ipv4" "127.0.0.1"
            "peer.port" Integer
            "span.kind" "server"
            "span.origin.type" "org.apache.catalina.core.ApplicationFilterChain"
            "component" "java-web-servlet"
            "http.status_code" 401
            defaultTags(true)
          }
        }
        controllerSpan(it, 1, "BasicErrorController.error")
      }
      trace(1, 1) {
        span(0) {
          operationName "servlet.request"
          resourceName "GET /param/?/"
          spanType DDSpanTypes.HTTP_SERVER
          childOf(trace(2).get(0))
          errored false
          tags {
            "http.url" "http://localhost:$port/param/asdf1234/"
            "http.method" "GET"
            "peer.hostname" "127.0.0.1"
            "peer.ipv4" "127.0.0.1"
            "peer.port" Integer
            "span.kind" "server"
            "span.origin.type" "org.apache.catalina.core.ApplicationFilterChain"
            "component" "java-web-servlet"
            "http.status_code" 401
            defaultTags(true)
          }
        }
      }
      trace(2, 1) {
        span(0) {
          operationName "http.request"
          resourceName "GET /param/?/"
          spanType DDSpanTypes.HTTP_CLIENT
          parent()
          errored false
          tags {
            "http.url" "http://localhost:$port/param/asdf1234/"
            "http.method" "GET"
            "span.kind" "client"
            "component" "rest-template"
            "http.status_code" 401
            "peer.hostname" "localhost"
            "peer.port" port
            defaultTags()
          }
        }
      }
    }
  }

  def "generates 404 spans"() {
    setup:
    def response = restTemplate.withBasicAuth(USER, PASS)
      .getForObject("http://localhost:$port/invalid", Map)

    expect:
    response.get("status") == 404
    response.get("error") == "Not Found"

    waitAndSortTraces(3)
    assertTraces(3) {
      trace(0, 2) {
        span(0) {
          operationName "servlet.request"
          resourceName "404"
          spanType DDSpanTypes.HTTP_SERVER
          childOf(trace(2).get(0))
          errored false
          tags {
            "http.url" "http://localhost:$port/invalid"
            "http.method" "GET"
            "peer.hostname" "127.0.0.1"
            "peer.ipv4" "127.0.0.1"
            "peer.port" Integer
            "span.kind" "server"
            "span.origin.type" "org.apache.catalina.core.ApplicationFilterChain"
            "component" "java-web-servlet"
            "http.status_code" 404
            "$DDTags.USER_NAME" test.Application.USER
            defaultTags(true)
          }
        }
        controllerSpan(it, 1, "ResourceHttpRequestHandler.handleRequest")
      }
      trace(1, 2) {
        span(0) {
          operationName "servlet.request"
          resourceName "404"
          spanType DDSpanTypes.HTTP_SERVER
          childOf(trace(2).get(0))
          errored false
          tags {
            "http.url" "http://localhost:$port/error"
            "http.method" "GET"
            "peer.hostname" "127.0.0.1"
            "peer.ipv4" "127.0.0.1"
            "peer.port" Integer
            "span.kind" "server"
            "span.origin.type" "org.apache.catalina.core.ApplicationFilterChain"
            "component" "java-web-servlet"
            "http.status_code" 404
            "$DDTags.USER_NAME" test.Application.USER
            defaultTags(true)
          }
        }
        controllerSpan(it, 1, "BasicErrorController.error")
      }
      trace(2, 1) {
        span(0) {
          operationName "http.request"
          resourceName "404"
          spanType DDSpanTypes.HTTP_CLIENT
          parent()
          errored false
          tags {
            "http.url" "http://localhost:$port/invalid"
            "http.method" "GET"
            "span.kind" "client"
            "component" "rest-template"
            "http.status_code" 404
            "peer.hostname" "localhost"
            "peer.port" port
            defaultTags()
          }
        }
      }
    }
  }

  def "generates error spans"() {
    setup:
    def response = restTemplate.withBasicAuth(USER, PASS)
      .getForObject("http://localhost:$port/error/qwerty/", Map)

    expect:
    response.get("status") == 500
    response.get("error") == "Internal Server Error"
    response.get("message") == "qwerty"

    waitAndSortTraces(3)
    assertTraces(3) {
      trace(0, 2) {
        span(0) {
          operationName "servlet.request"
          resourceName "GET /error/{parameter}/"
          spanType DDSpanTypes.HTTP_SERVER
          childOf(trace(2).get(0))
          errored true
          tags {
            "http.url" "http://localhost:$port/error/qwerty/"
            "http.method" "GET"
            "peer.hostname" "127.0.0.1"
            "peer.ipv4" "127.0.0.1"
            "peer.port" Integer
            "span.kind" "server"
            "span.origin.type" "org.apache.catalina.core.ApplicationFilterChain"
            "component" "java-web-servlet"
            "http.status_code" 500
            "$DDTags.USER_NAME" test.Application.USER
            errorTags NestedServletException, "Request processing failed; nested exception is java.lang.RuntimeException: qwerty"
            defaultTags(true)
          }
        }
        controllerSpan(it, 1, "TestController.withError", RuntimeException)
      }
      trace(1, 2) {
        span(0) {
          operationName "servlet.request"
          resourceName "GET /error"
          spanType DDSpanTypes.HTTP_SERVER
          childOf(trace(2).get(0))
          errored true
          tags {
            "http.url" "http://localhost:$port/error"
            "http.method" "GET"
            "peer.hostname" "127.0.0.1"
            "peer.ipv4" "127.0.0.1"
            "peer.port" Integer
            "span.kind" "server"
            "span.origin.type" "org.apache.catalina.core.ApplicationFilterChain"
            "component" "java-web-servlet"
            "http.status_code" 500
            "$DDTags.USER_NAME" test.Application.USER
            "error" true
            defaultTags(true)
          }
        }
        controllerSpan(it, 1, "BasicErrorController.error")
      }
      trace(2, 1) {
        span(0) {
          operationName "http.request"
          resourceName "GET /error/qwerty/"
          spanType DDSpanTypes.HTTP_CLIENT
          parent()
          errored true
          tags {
            "http.url" "http://localhost:$port/error/qwerty/"
            "http.method" "GET"
            "span.kind" "client"
            "component" "rest-template"
            "http.status_code" 500
            "peer.hostname" "localhost"
            "peer.port" port
            "error" true
            defaultTags()
          }
        }
      }
    }
  }

  def "validated form"() {
    expect:
    restTemplate.withBasicAuth(USER, PASS)
      .postForObject("http://localhost:$port/validated", new test.TestForm("bob", 20), String) == "Hello bob Person(Name: bob, Age: 20)"

    waitAndSortTraces(2)
    assertTraces(2) {
      trace(0, 2) {
        span(0) {
          operationName "servlet.request"
          resourceName "POST /validated"
          spanType DDSpanTypes.HTTP_SERVER
          childOf(trace(1).get(0))
          errored false
          tags {
            "http.url" "http://localhost:$port/validated"
            "http.method" "POST"
            "peer.hostname" "127.0.0.1"
            "peer.ipv4" "127.0.0.1"
            "peer.port" Integer
            "span.kind" "server"
            "span.origin.type" "org.apache.catalina.core.ApplicationFilterChain"
            "component" "java-web-servlet"
            "http.status_code" 200
            "$DDTags.USER_NAME" test.Application.USER
            defaultTags(true)
          }
        }
        controllerSpan(it, 1, "TestController.withValidation")
      }
      trace(1, 1) {
        span(0) {
          operationName "http.request"
          resourceName "POST /validated"
          spanType DDSpanTypes.HTTP_CLIENT
          parent()
          errored false
          tags {
            "http.url" "http://localhost:$port/validated"
            "http.method" "POST"
            "span.kind" "client"
            "component" "rest-template"
            "http.status_code" 200
            "peer.hostname" "localhost"
            "peer.port" port
            defaultTags()
          }
        }
      }
    }
  }

  def "invalid form"() {
    setup:
    def response = restTemplate.withBasicAuth(USER, PASS)
      .postForObject("http://localhost:$port/validated", new test.TestForm("bill", 5), Map, Map)

    expect:
    response.get("status") == 400
    response.get("error") == "Bad Request"
    response.get("message") == "Validation failed for object='testForm'. Error count: 1"

    waitAndSortTraces(3)
    assertTraces(3) {
      trace(0, 2) {
        span(0) {
          operationName "servlet.request"
          resourceName "POST /validated"
          spanType DDSpanTypes.HTTP_SERVER
          childOf(trace(2).get(0))
          errored false
          tags {
            "http.url" "http://localhost:$port/validated"
            "http.method" "POST"
            "peer.hostname" "127.0.0.1"
            "peer.ipv4" "127.0.0.1"
            "peer.port" Integer
            "span.kind" "server"
            "span.origin.type" "org.apache.catalina.core.ApplicationFilterChain"
            "component" "java-web-servlet"
            "http.status_code" 400
            "$DDTags.USER_NAME" test.Application.USER
            "error" false
            "error.msg" String
            "error.type" MethodArgumentNotValidException.name
            "error.stack" String
            defaultTags(true)
          }
        }
        controllerSpan(it, 1, "TestController.withValidation", MethodArgumentNotValidException)
      }
      trace(1, 2) {
        span(0) {
          operationName "servlet.request"
          resourceName "POST /error"
          spanType DDSpanTypes.HTTP_SERVER
          childOf(trace(2).get(0))
          errored false
          tags {
            "http.url" "http://localhost:$port/error"
            "http.method" "POST"
            "peer.hostname" "127.0.0.1"
            "peer.ipv4" "127.0.0.1"
            "peer.port" Integer
            "span.kind" "server"
            "span.origin.type" "org.apache.catalina.core.ApplicationFilterChain"
            "component" "java-web-servlet"
            "http.status_code" 400
            "$DDTags.USER_NAME" test.Application.USER
            defaultTags(true)
          }
        }
        controllerSpan(it, 1, "BasicErrorController.error")
      }
      trace(2, 1) {
        span(0) {
          operationName "http.request"
          resourceName "POST /validated"
          spanType DDSpanTypes.HTTP_CLIENT
          parent()
          errored false
          tags {
            "http.url" "http://localhost:$port/validated"
            "http.method" "POST"
            "span.kind" "client"
            "component" "rest-template"
            "http.status_code" 400
            "peer.hostname" "localhost"
            "peer.port" port
            defaultTags()
          }
        }
      }
    }
  }

  def waitAndSortTraces(int num) {
    TEST_WRITER.waitForTraces(num)
    TEST_WRITER.sort{a,b ->
      if (a.size() < b.size()) {
        return 1
      } else if (a.size() > b.size()) {
        return -1
      } else if (a.get(0).tags.get(Tags.COMPONENT.key) == "rest-template") {
        return 1
      }
      return 0
    }
  }

  def controllerSpan(TraceAssert trace, int index, String name, Class<Throwable> errorType = null) {
    trace.span(index) {
      serviceName "unnamed-java-app"
      operationName name
      resourceName name
      spanType DDSpanTypes.HTTP_SERVER
      childOf(trace.span(0))
      errored errorType != null
      tags {
        "$Tags.COMPONENT.key" "spring-web-controller"
        "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_SERVER
        if (errorType) {
          "error.msg" String
          errorTags(errorType)
        }
        defaultTags()
      }
    }
  }
}

