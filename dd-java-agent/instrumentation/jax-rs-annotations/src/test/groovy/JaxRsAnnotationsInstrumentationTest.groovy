// Modified by SignalFx
import datadog.trace.agent.test.AgentTestRunner
import io.opentracing.tag.Tags

import javax.ws.rs.DELETE
import javax.ws.rs.GET
import javax.ws.rs.HEAD
import javax.ws.rs.OPTIONS
import javax.ws.rs.POST
import javax.ws.rs.PUT
import javax.ws.rs.Path

import static datadog.trace.agent.test.TestUtils.runUnderTrace

class JaxRsAnnotationsInstrumentationTest extends AgentTestRunner {

  def "span named #httpMethod #path from annotations on class"() {
    setup:
    obj.call()

    expect:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          operationName "$httpMethod $path".trim()
          parent()
          tags {
            "$Tags.SPAN_KIND.key" "$Tags.SPAN_KIND_SERVER"
            "$Tags.COMPONENT.key" "jax-rs"
            if (path != ""){
              "$Tags.HTTP_URL.key" path
            }
            if (httpMethod != "") {
              "$Tags.HTTP_METHOD.key" httpMethod
            }
            defaultTags()
          }
        }
        span(1) {
          operationName "${className}.call"
          childOf span(0)
          tags {
            "$Tags.COMPONENT.key" "jax-rs-controller"
            defaultTags()
          }
        }
      }
    }

    where:
    httpMethod | path                   | obj
    ""         | "/a"                   | new Jax() {
      @Path("/a")
      void call() {}
    }
    "GET"      | "/b"                   | new Jax() {
      @GET
      @Path("/b")
      void call() {}
    }
    "POST"     | "/c"                   | new InterfaceWithPath() {
      @POST
      @Path("/c")
      void call() {}
    }
    "HEAD"     | ""                     | new InterfaceWithPath() {
      @HEAD
      void call() {}
    }
    "POST"     | "/abstract/d"          | new AbstractClassWithPath() {
      @POST
      @Path("/d")
      void call() {}
    }
    "PUT"      | "/abstract"            | new AbstractClassWithPath() {
      @PUT
      void call() {}
    }
    "OPTIONS"  | "/abstract/child/e"    | new ChildClassWithPath() {
      @OPTIONS
      @Path("/e")
      void call() {}
    }
    "DELETE"   | "/abstract/child"      | new ChildClassWithPath() {
      @DELETE
      void call() {}
    }
    "POST"     | "/abstract/child/call" | new ChildClassWithPath()

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
          tags {
            defaultTags()
          }
        }
      }
    }

    where:
    obj | _
    new Jax() {
      void call() {}
    }   | _
    new InterfaceWithPath() {
      void call() {}
    }   | _
    new AbstractClassWithPath() {
      void call() {}
    }   | _
    new ChildClassWithPath() {
      void call() {}
    }   | _
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
    void call() {}
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
