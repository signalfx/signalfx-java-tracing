// Modified by SignalFx
import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.utils.PortUtils
import datadog.trace.api.DDSpanTypes
import datadog.trace.instrumentation.netty40.NettyUtils
import io.opentracing.tag.Tags
import org.asynchttpclient.AsyncHttpClient
import org.asynchttpclient.DefaultAsyncHttpClientConfig
import spock.lang.AutoCleanup
import spock.lang.Shared

import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

import static datadog.trace.agent.test.server.http.TestHttpServer.httpServer
import static datadog.trace.agent.test.utils.TraceUtils.runUnderTrace
import static org.asynchttpclient.Dsl.asyncHttpClient

class Netty40ClientTest extends AgentTestRunner {

  @AutoCleanup
  @Shared
  def server = httpServer {
    handlers {
      post("/post") {
        int sc = request.headers.get("X-Status-Code").toInteger()
        response.status(sc).send("Received")
      }
      get("/get") {
        response.send("Hello World")
      }
    }
  }
  @Shared
  def clientConfig = DefaultAsyncHttpClientConfig.Builder.newInstance().setRequestTimeout(TimeUnit.SECONDS.toMillis(10).toInteger())
  @Shared
  AsyncHttpClient asyncHttpClient = asyncHttpClient(clientConfig)

  def "test server request/response"() {
    setup:
    def responseFuture = runUnderTrace("parent") {
      asyncHttpClient.prepareGet("${server.address}/get").execute()
    }
    def response = responseFuture.get()

    expect:
    response.statusCode == 200
    response.responseBody == "Hello World"

    and:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          serviceName "unnamed-java-app"
          operationName "netty.client.request"
          resourceName "/get"
          spanType DDSpanTypes.HTTP_CLIENT
          childOf span(1)
          errored false
          tags {
            "$Tags.COMPONENT.key" "netty-client"
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.HTTP_STATUS.key" 200
            "$Tags.HTTP_URL.key" "$server.address/get"
            "$Tags.PEER_HOSTNAME.key" "localhost"
            "$Tags.PEER_HOST_IPV4.key" "127.0.0.1"
            "$Tags.PEER_PORT.key" server.address.port
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            defaultTags()
          }
        }
        span(1) {
          operationName "parent"
          parent()
        }
      }
    }

    and:
    server.lastRequest.headers.get("x-b3-traceid") == new BigInteger(TEST_WRITER.get(0).get(0).traceId).toString(16).toLowerCase()
    server.lastRequest.headers.get("x-b3-spanid") == new BigInteger(TEST_WRITER.get(0).get(0).spanId).toString(16).toLowerCase()
  }

  def "test connection failure"() {
    setup:
    def invalidPort = PortUtils.randomOpenPort()

    def responseFuture = runUnderTrace("parent") {
      asyncHttpClient.prepareGet("http://localhost:$invalidPort/").execute()
    }

    when:
    responseFuture.get()

    then:
    def throwable = thrown(ExecutionException)
    throwable.cause instanceof ConnectException

    and:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          operationName "parent"
          parent()
        }
        span(1) {
          operationName "netty.connect"
          resourceName "netty.connect"
          childOf span(0)
          errored true
          tags {
            "$Tags.COMPONENT.key" "netty"
            Class errorClass = ConnectException
            try {
              errorClass = Class.forName('io.netty.channel.AbstractChannel$AnnotatedConnectException')
            } catch (ClassNotFoundException e) {
              // Older versions use 'java.net.ConnectException' and do not have 'io.netty.channel.AbstractChannel$AnnotatedConnectException'
            }
            errorTags errorClass, "Connection refused: localhost/127.0.0.1:$invalidPort"
            defaultTags()
          }
        }
      }
    }
  }

  def "test #statusCode statusCode rewrite #rewrite"() {
    setup:
    def property = "signalfx.${NettyUtils.NETTY_REWRITTEN_CLIENT_STATUS_PREFIX}$statusCode"
    System.getProperties().setProperty(property, "$rewrite")

    def responseFuture = runUnderTrace("parent") {
      asyncHttpClient.preparePost("${server.address}/post")
        .setHeader("X-Status-Code", statusCode.toString())
        .execute()
    }
    def response = responseFuture.get()

    expect:
    response.statusCode == statusCode
    response.responseBody == "Received"

    and:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          serviceName "unnamed-java-app"
          operationName "netty.client.request"
          resourceName "/post"
          spanType DDSpanTypes.HTTP_CLIENT
          childOf span(1)
          errored error
          tags {
            "$Tags.COMPONENT.key" "netty-client"
            "$Tags.HTTP_METHOD.key" "POST"
            if (rewrite) {
              "$Tags.HTTP_STATUS.key" null
              "$NettyUtils.ORIG_HTTP_STATUS.key" statusCode
            } else {
              "$Tags.HTTP_STATUS.key" statusCode
            }
            "$Tags.HTTP_URL.key" "$server.address/post"
            "$Tags.PEER_HOSTNAME.key" "localhost"
            "$Tags.PEER_HOST_IPV4.key" "127.0.0.1"
            "$Tags.PEER_PORT.key" Integer
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            if (error) {
              tag("error", true)
            }
            defaultTags()
          }
        }
        span(1) {
          operationName "parent"
          parent()
        }
      }
    }

    and:
    server.lastRequest.headers.get("x-b3-traceid") == new BigInteger(TEST_WRITER.get(0).get(0).traceId).toString(16).toLowerCase()
    server.lastRequest.headers.get("x-b3-spanid") == new BigInteger(TEST_WRITER.get(0).get(0).spanId).toString(16).toLowerCase()

    where:
    statusCode | error | rewrite
    200        | false | false
    200        | false | true
    500        | true  | false
    500        | false | true
    502        | true  | false
    502        | false | true
    503        | true  | false
    503        | false | true
  }
}
