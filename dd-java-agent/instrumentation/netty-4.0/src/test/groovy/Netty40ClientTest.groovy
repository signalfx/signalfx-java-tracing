// Modified by SignalFx
import datadog.trace.api.DDSpanTypes
import datadog.trace.instrumentation.netty40.NettyUtils
import datadog.trace.agent.test.base.HttpClientTest
import datadog.trace.instrumentation.api.Tags
import datadog.trace.instrumentation.netty40.client.NettyHttpClientDecorator
import org.asynchttpclient.AsyncCompletionHandler
import org.asynchttpclient.AsyncHttpClient
import org.asynchttpclient.DefaultAsyncHttpClientConfig
import org.asynchttpclient.Response
import spock.lang.AutoCleanup
import spock.lang.Shared

import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

import static datadog.trace.agent.test.server.http.TestHttpServer.httpServer
import static datadog.trace.agent.test.utils.PortUtils.UNUSABLE_PORT
import static datadog.trace.agent.test.utils.TraceUtils.basicSpan
import static datadog.trace.agent.test.utils.TraceUtils.runUnderTrace
import static org.asynchttpclient.Dsl.asyncHttpClient

class Netty40ClientTest extends HttpClientTest<NettyHttpClientDecorator> {

  @Shared
  def clientConfig = DefaultAsyncHttpClientConfig.Builder.newInstance().setRequestTimeout(TimeUnit.SECONDS.toMillis(10).toInteger())
  @Shared
  @AutoCleanup
  AsyncHttpClient asyncHttpClient = asyncHttpClient(clientConfig)

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers, Closure callback) {
    def methodName = "prepare" + method.toLowerCase().capitalize()
    def requestBuilder = asyncHttpClient."$methodName"(uri.toString())
    headers.each { requestBuilder.setHeader(it.key, it.value) }
    def response = requestBuilder.execute(new AsyncCompletionHandler() {
      @Override
      Object onCompleted(Response response) throws Exception {
        callback?.call()
        return response
      }
    }).get()
    blockUntilChildSpansFinished(1)
    return response.statusCode
  }

  @Override
  NettyHttpClientDecorator decorator() {
    return NettyHttpClientDecorator.DECORATE
  }

  @Override
  String expectedOperationName() {
    return "netty.client.request"
  }

  @Override
  boolean testRedirects() {
    false
  }

  @Override
  boolean testConnectionFailure() {
    false
  }

  def "connection error (unopened port)"() {
    given:
    def uri = new URI("http://localhost:$UNUSABLE_PORT/")

    when:
    runUnderTrace("parent") {
      doRequest(method, uri)
    }

    then:
    def ex = thrown(Exception)
    def thrownException = ex instanceof ExecutionException ? ex.cause : ex

    and:
    assertTraces(1) {
      trace(0, 2) {
        basicSpan(it, 0, "parent", null, thrownException)

        span(1) {
          operationName "netty.connect"
          resourceName "netty.connect"
          childOf span(0)
          errored true
          tags {
            "$Tags.COMPONENT" "netty"
            Class errorClass = ConnectException
            try {
              errorClass = Class.forName('io.netty.channel.AbstractChannel$AnnotatedConnectException')
            } catch (ClassNotFoundException e) {
              // Older versions use 'java.net.ConnectException' and do not have 'io.netty.channel.AbstractChannel$AnnotatedConnectException'
            }
            errorTags errorClass, "Connection refused: localhost/127.0.0.1:$UNUSABLE_PORT"
            defaultTags()
          }
        }
      }
    }

    where:
    method = "GET"
  }

  def "test #statusCode statusCode rewrite #rewrite"() {
    setup:
    def property = "signalfx.${NettyUtils.NETTY_REWRITTEN_CLIENT_STATUS_PREFIX}$statusCode"
    System.getProperties().setProperty(property, "$rewrite")

    server = httpServer {
      handlers {
        post("/post") {
          int sc = request.headers.get("X-Status-Code").toInteger()
          response.status(sc).send("Received")
        }
      }
    }

    def resStatusCode
    runUnderTrace("parent") {
      resStatusCode = doRequest("POST", server.address.resolve("/post"), ["X-Status-Code" : statusCode.toString()])
    }

    expect:
    resStatusCode == statusCode

    and:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          operationName "parent"
          parent()
        }
        span(1) {
          serviceName "unnamed-java-app"
          operationName "netty.client.request"
          resourceName "/post"
          spanType DDSpanTypes.HTTP_CLIENT
          childOf span(0)
          errored error
          tags {
            "$Tags.COMPONENT" "netty-client"
            "$Tags.HTTP_METHOD" "POST"
            if (rewrite) {
              "$Tags.HTTP_STATUS" null
              "$NettyUtils.ORIG_HTTP_STATUS" statusCode
            } else {
              "$Tags.HTTP_STATUS" statusCode
            }
            "$Tags.HTTP_URL" "$server.address/post"
            "$Tags.PEER_HOSTNAME" "localhost"
            "$Tags.PEER_HOST_IPV4" "127.0.0.1"
            "$Tags.PEER_PORT" Integer
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            if (error) {
              tag("error", true)
            }
            defaultTags()
          }
        }
      }
    }

    and:
    server.lastRequest.headers.get("x-b3-traceid") == new BigInteger(TEST_WRITER.get(0).get(1).traceId).toString(16).toLowerCase()
    server.lastRequest.headers.get("x-b3-spanid") == new BigInteger(TEST_WRITER.get(0).get(1).spanId).toString(16).toLowerCase()

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
