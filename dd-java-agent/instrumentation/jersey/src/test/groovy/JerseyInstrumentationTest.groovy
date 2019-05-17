// Modified by SignalFx

import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.utils.OkHttpUtils
import datadog.trace.agent.test.utils.PortUtils

import io.opentracing.tag.Tags
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.glassfish.grizzly.http.server.HttpServer
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory
import org.glassfish.jersey.server.ResourceConfig


class JerseyInstrumentationTest extends AgentTestRunner {


  OkHttpClient client = OkHttpUtils.client()

  def "extracts distributed context"() {
    setup:
    int port = PortUtils.randomOpenPort()
    HttpServer server = setupServer(port)

    def client = new OkHttpClient()

    def plainType = okhttp3.MediaType.parse("text/plain; charset=utf-8")
    def body = RequestBody.create(plainType, "")
    def request = new Request.Builder()
      .url("http://localhost:$port/test/hello/bob")
      .header("x-datadog-trace-id", "123")
      .header("x-datadog-parent-id", "456")
      .post(body)
      .build()
    def response = client.newCall(request).execute()

    expect:
    response.code() == 200
    response.body().string() == "Hello bob!"

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          traceId "123"
          parentId "456"
          serviceName "unnamed-java-app"
          operationName "jersey.request"
          resourceName "POST /test/hello/bob"
          errored false
          tags {
            defaultTags(true)
            "$Tags.HTTP_STATUS.key" 200
            "$Tags.HTTP_URL.key" "http://localhost:$port/test/hello/bob"
            "$Tags.HTTP_METHOD.key" "POST"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_SERVER
            "$Tags.COMPONENT.key" "jersey"
          }
        }
      }
    }

    cleanup:
    server.stop()
  }

  def "handles errors"() {
    setup:
    int port = PortUtils.randomOpenPort()
    HttpServer server = setupServer(port)

    def client = new OkHttpClient()
    def request = new Request.Builder()
      .url("http://localhost:$port/test/blowup")
      .header("x-datadog-trace-id", "123")
      .header("x-datadog-parent-id", "456")
      .get()
      .build()
    def response = client.newCall(request).execute()

    expect:
    response.code() == 500

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          traceId "123"
          parentId "456"
          serviceName "unnamed-java-app"
          operationName "jersey.request"
          resourceName "GET /test/blowup"
          errored true
          tags {
            defaultTags(true)
            "$Tags.HTTP_STATUS.key" 500
            "$Tags.HTTP_URL.key" "http://localhost:$port/test/blowup"
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_SERVER
            "$Tags.COMPONENT.key" "jersey"
            // Raised exception varies by version so don't use TagsAssert helpers
            "error" true
            "error.type" String
            "error.stack" String
            "error.msg" String
          }
        }
      }
    }

    cleanup:
    server.stop()
  }

  def setupServer(port) {
    ResourceConfig rc = new ResourceConfig().register(TestResource)
    return GrizzlyHttpServerFactory.createHttpServer(URI.create("http://localhost:$port"), rc)

  }
}

