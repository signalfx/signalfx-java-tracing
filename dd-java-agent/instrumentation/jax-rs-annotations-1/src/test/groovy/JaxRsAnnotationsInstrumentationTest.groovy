// Modified by SignalFx
import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.instrumentation.api.Tags
import com.signalfx.tracing.api.TraceSetting

import javax.ws.rs.DELETE
import javax.ws.rs.GET
import javax.ws.rs.HEAD
import javax.ws.rs.OPTIONS
import javax.ws.rs.POST
import javax.ws.rs.PUT
import javax.ws.rs.Path

import static datadog.trace.agent.test.utils.TraceUtils.runUnderTrace
import static datadog.trace.instrumentation.jaxrs1.JaxRsAnnotationsDecorator.DECORATE

class JaxRsAnnotationsInstrumentationTest extends AgentTestRunner {

  def "instrumentation can be used as root span and resource is set to PATH"() {
    setup:
    new Jax() {
      @POST
      @Path("/a")
      void call() {
      }
    }.call()

    expect:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          operationName "jax-rs.request"
          resourceName "/a"
          spanType "web"
          tags {
            "$Tags.COMPONENT" "jax-rs-controller"
            "$Tags.HTTP_URL" "/a"
            "$Tags.HTTP_METHOD" "POST"
            "$Tags.SPAN_KIND" "$Tags.SPAN_KIND_SERVER"
            defaultTags()
          }
        }
      }
    }
  }

  def "span named '#name' from annotations on class when is not root span"() {
    setup:
    def startingCacheSize = DECORATE.resourceNames.size()
    runUnderTrace("test") {
      obj.call()
    }

    expect:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          operationName "test"
          resourceName name
          parent()
          tags {
            "$Tags.SPAN_KIND" "$Tags.SPAN_KIND_SERVER"
            defaultTags()
          }
        }
        span(1) {
          operationName "jax-rs.request"
          resourceName "${className}.call"
          spanType "web"
          childOf span(0)
          tags {
            "$Tags.COMPONENT" "jax-rs-controller"
            "$Tags.HTTP_METHOD" method
            "$Tags.HTTP_URL" url
            defaultTags()
          }
        }
      }
    }
    DECORATE.resourceNames.size() == startingCacheSize + 1
    DECORATE.resourceNames.get(obj.class).size() == 1

    when: "multiple calls to the same method"
    runUnderTrace("test") {
      (1..10).each {
        obj.call()
      }
    }
    then: "doesn't increase the cache size"
    DECORATE.resourceNames.size() == startingCacheSize + 1
    DECORATE.resourceNames.get(obj.class).size() == 1

    where:
    name | method | url           | obj
    "/a" | null   | "/a"          | new Jax() {
      @Path("/a")
      void call() {
      }
    }
    "/b" | "GET" | "/b"           | new Jax() {
      @GET
      @Path("/b")
      void call() {
      }
    }
    "/c" | "POST"  | "/c"         | new InterfaceWithPath() {
      @POST
      @Path("/c")
      void call() {
      }
    }
    "test" | "HEAD"  | null       | new InterfaceWithPath() {
      @HEAD
      void call() {
      }
    }
    "/abstract/d" | "POST" | "/abstract/d"  | new AbstractClassWithPath() {
      @POST
      @Path("/d")
      void call() {
      }
    }
    "/abstract" | "PUT" | "/abstract"       | new AbstractClassWithPath() {
      @PUT
      void call() {
      }
    }
    "/abstract/child/e" | "OPTIONS" | "/abstract/child/e" | new ChildClassWithPath() {
      @OPTIONS
      @Path("/e")
      void call() {
      }
    }
    "/abstract/child" | "DELETE" | "/abstract/child"  | new ChildClassWithPath() {
      @DELETE
      void call() {
      }
    }
    "/abstract/child/call" | "POST" | "/abstract/child/call" | new ChildClassWithPath()

    className = getName(obj.class)
  }

  def "no annotations has no effect"() {
    setup:
    runUnderTrace("test") {
      obj.call()
    }

    expect:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          operationName "test"
          resourceName "test"
          tags {
            defaultTags()
          }
        }
      }
    }

    where:
    obj | _
    new Jax() {
      void call() {
      }
    }   | _
    new InterfaceWithPath() {
      void call() {
      }
    }   | _
    new AbstractClassWithPath() {
      void call() {
      }
    }   | _
    new ChildClassWithPath() {
      void call() {
      }
    }   | _
  }

  def "Setting allowedExceptions doesn't tag error #error"() {
    setup:
    try {
      obj.call()
    } catch (Throwable exc) {
    }

    expect:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          operationName "jax-rs.request"
          resourceName "/"
          spanType "web"
          parent()
          errored error
          tags {
            "$Tags.COMPONENT" "jax-rs-controller"
            "$Tags.SPAN_KIND" "$Tags.SPAN_KIND_SERVER"
            "$Tags.HTTP_URL" "/"
            "$Tags.HTTP_METHOD" "GET"
            if (error) {
              tag('error', true)
              tag('error.stack', String)
              tag('error.type', errorType)
            }
            defaultTags()
          }
        }
      }
    }

    where:
    error | errorType                           | obj
    false | java.lang.NullPointerException.name | new Jax() {
      @GET
      @TraceSetting(allowedExceptions = [java.lang.NullPointerException])
      @Path("/")
      void call() throws Exception {
        throw new java.lang.NullPointerException()
      }
    }
    true  | Exception.name                      | new Jax() {
      @GET
      @TraceSetting(allowedExceptions = [java.lang.IncompatibleClassChangeError])
      @Path("/")
      void call() throws Exception {
        throw new Exception()
      }
    }

    className = getName(obj.class)
  }

  interface Jax {
    void call()
  }

  @Path("/interface")
  interface InterfaceWithPath extends Jax {
    @GET
    void call()
  }

  @Path("/abstract")
  abstract class AbstractClassWithPath implements Jax {
    @PUT
    abstract void call()
  }

  @Path("child")
  class ChildClassWithPath extends AbstractClassWithPath {
    @Path("call")
    @POST
    void call() {
    }
  }

  def getName(Class clazz) {
    String className = clazz.getSimpleName()
    if (className.isEmpty()) {
      className = clazz.getName()
      if (clazz.getPackage() != null) {
        final String pkgName = clazz.getPackage().getName()
        if (!pkgName.isEmpty()) {
          className = clazz.getName().replace(pkgName, "").substring(1)
        }
      }
    }
    return className
  }
}
