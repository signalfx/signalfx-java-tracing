// Modified by SignalFx
import datadog.trace.agent.test.AgentTestRunner
import io.opentracing.tag.Tags
import org.apache.cxf.jaxrs.client.spec.ClientBuilderImpl
import org.glassfish.jersey.client.JerseyClientBuilder
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder
import spock.lang.AutoCleanup
import spock.lang.Shared

import javax.ws.rs.ClientErrorException
import javax.ws.rs.ProcessingException
import javax.ws.rs.RedirectionException
import javax.ws.rs.client.AsyncInvoker
import javax.ws.rs.client.Client
import javax.ws.rs.client.Invocation
import javax.ws.rs.client.WebTarget
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response
import java.util.concurrent.ExecutionException

import static datadog.trace.agent.test.server.http.TestHttpServer.httpServer
import static datadog.trace.agent.test.utils.PortUtils.UNUSABLE_PORT

class JaxRsClientTest extends AgentTestRunner {

  @AutoCleanup
  @Shared
  def server = httpServer {
    handlers {
      get ("/redirect") {
        response.status(301).send()
      }
      get ("/client") {
        response.status(401).send()
      }
      all {
        response.status(200).send("pong")
      }
    }
  }

  def "#lib request creates spans and sends headers"() {
    setup:
    Client client = builder.build()
    WebTarget service = client.target("$server.address/ping")
    Response response
    if (async) {
      AsyncInvoker request = service.request(MediaType.TEXT_PLAIN).async()
      response = request.get().get()
    } else {
      Invocation.Builder request = service.request(MediaType.TEXT_PLAIN)
      response = request.get()
    }

    expect:
    response.readEntity(String) == "pong"

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          serviceName "unnamed-java-app"
          resourceName "/ping"
          operationName "jax-rs.client.call"
          spanType "http"
          parent()
          errored false
          tags {
            "$Tags.COMPONENT.key" "jax-rs.client"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.HTTP_STATUS.key" 200
            "$Tags.HTTP_URL.key" "$server.address/ping"
            "$Tags.PEER_HOSTNAME.key" "localhost"
            "$Tags.PEER_PORT.key" server.address.port
            defaultTags()
          }
        }
      }
    }

    server.lastRequest.headers.get("x-b3-traceid") == new BigInteger(TEST_WRITER[0][0].traceId).toString(16).toLowerCase()
    server.lastRequest.headers.get("x-b3-spanid") == new BigInteger(TEST_WRITER[0][0].spanId).toString(16).toLowerCase()

    where:
    builder                     | async | lib
    new JerseyClientBuilder()   | false | "jersey"
    new ClientBuilderImpl()     | false | "cxf"
    new ResteasyClientBuilder() | false | "resteasy"
    new JerseyClientBuilder()   | true  | "jersey async"
    new ClientBuilderImpl()     | true  | "cxf async"
    new ResteasyClientBuilder() | true  | "resteasy async"
  }

  def "#lib connection failure creates errored span"() {
    when:
    Client client = builder.build()
    WebTarget service = client.target("http://localhost:$UNUSABLE_PORT/ping")
    if (async) {
      AsyncInvoker request = service.request(MediaType.TEXT_PLAIN).async()
      request.get().get()
    } else {
      Invocation.Builder request = service.request(MediaType.TEXT_PLAIN)
      request.get()
    }

    then:
    thrown async ? ExecutionException : ProcessingException

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          serviceName "unnamed-java-app"
          resourceName "/ping"
          operationName "jax-rs.client.call"
          spanType "http"
          parent()
          errored true
          tags {
            "$Tags.COMPONENT.key" "jax-rs.client"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.HTTP_URL.key" "http://localhost:$UNUSABLE_PORT/ping"
            "$Tags.PEER_HOSTNAME.key" "localhost"
            "$Tags.PEER_PORT.key" UNUSABLE_PORT
            errorTags ProcessingException, String
            defaultTags()
          }
        }
      }
    }

    where:
    builder                     | async | lib
    new JerseyClientBuilder()   | false | "jersey"
    new ResteasyClientBuilder() | false | "resteasy"
    new JerseyClientBuilder()   | true  | "jersey async"
    new ResteasyClientBuilder() | true  | "resteasy async"
    // Unfortunately there's not a good way to instrument this for CXF.
  }

  def "#lib #errorType error handled without error tag"() {
    setup:
    Client client = builder.build()
    WebTarget service = client.target("$server.address/$errorType")
    def expectedException = [redirect: RedirectionException, client: ClientErrorException][(errorType)]
    def expectedStatusCode = [redirect: 301, client: 401][(errorType)]

    def errorThrown = false
    if (async) {
      AsyncInvoker request = service.request(MediaType.TEXT_PLAIN).async()
      try {
        // Status-based exceptions are thrown during translation, so specify Object as entity type
        request.get(Object).get()
      } catch (ExecutionException e) {
        if (expectedException.isInstance(e.getCause())) {
          errorThrown = true
        }
      }
    } else {
      Invocation.Builder request = service.request(MediaType.TEXT_PLAIN)
      try {
        request.get(Object)
      } catch (Throwable e) {
        if (expectedException.isInstance(e)) {
          errorThrown = true
        }
      }
    }

    expect:
    errorThrown

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          serviceName "unnamed-java-app"
          resourceName "/$errorType"
          operationName "jax-rs.client.call"
          spanType "http"
          parent()
          errored false
          tags {
            "$Tags.COMPONENT.key" "jax-rs.client"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.HTTP_STATUS.key" expectedStatusCode
            "$Tags.HTTP_URL.key" "$server.address/$errorType"
            "$Tags.PEER_HOSTNAME.key" "localhost"
            "$Tags.PEER_PORT.key" server.address.port
            defaultTags()
          }
        }
      }
    }

    server.lastRequest.headers.get("x-b3-traceid") == new BigInteger(TEST_WRITER[0][0].traceId).toString(16).toLowerCase()
    server.lastRequest.headers.get("x-b3-spanid") == new BigInteger(TEST_WRITER[0][0].spanId).toString(16).toLowerCase()

    where:
    builder                     | async | errorType  | lib
    new JerseyClientBuilder()   | false | "redirect" | "jersey"
    new JerseyClientBuilder()   | false | "client"   | "jersey"
    new ClientBuilderImpl()     | false | "redirect" | "cxf"
    new ClientBuilderImpl()     | false | "client"   | "cxf"
    new ResteasyClientBuilder() | false | "redirect" | "resteasy"
    new ResteasyClientBuilder() | false | "client"   | "resteasy"
    new JerseyClientBuilder()   | true  | "redirect" | "jersey async"
    new JerseyClientBuilder()   | true  | "client"   | "jersey async"
    new ClientBuilderImpl()     | true  | "redirect" | "cxf async"
    new ClientBuilderImpl()     | true  | "client"   | "cxf async"
    new ResteasyClientBuilder() | true  | "redirect" | "resteasy async"
    new ResteasyClientBuilder() | true  | "client"   | "resteasy async"
  }
}
