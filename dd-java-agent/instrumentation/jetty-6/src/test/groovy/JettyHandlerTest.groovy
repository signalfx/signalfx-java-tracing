//Modified by SignalFx

import static datadog.trace.agent.test.utils.ConfigUtils.withConfigOverride

import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.utils.OkHttpUtils
import datadog.trace.agent.test.utils.PortUtils
import datadog.trace.api.Config
import datadog.trace.api.DDSpanTypes
import okhttp3.OkHttpClient
import org.mortbay.jetty.Handler
import org.mortbay.jetty.HttpConnection
import org.mortbay.jetty.Request
import org.mortbay.jetty.Server
import org.mortbay.jetty.handler.AbstractHandler

import javax.servlet.ServletException
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class JettyHandlerTest extends AgentTestRunner {

  int port = PortUtils.randomOpenPort()
  Server server = new Server(port)

  OkHttpClient client = OkHttpUtils.client()

  def cleanup() {
    server.stop()
  }

  def "call to jetty creates a trace"() {
    setup:
    Handler handler = new AbstractHandler() {
      @Override
      void handle(String target, HttpServletRequest request, HttpServletResponse response, int dispatch) throws IOException, ServletException {
        response.setContentType("text/plain;charset=utf-8")
        response.setStatus(HttpServletResponse.SC_OK)

        Request baseRequest = (request instanceof Request) ? (Request) request : HttpConnection.getCurrentConnection().getRequest()
        baseRequest.setHandled(true)
        response.getWriter().println("Hello World")
      }
    }
    server.setHandler(handler)
    server.start()
    def request = new okhttp3.Request.Builder()
      .url("http://localhost:$port/")
      .get()
      .build()
    def response = client.newCall(request).execute()

    expect:
    response.body().string().trim() == "Hello World"

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          serviceName "unnamed-java-service"
          operationName "jetty.request"
          resourceName "/"
          spanType DDSpanTypes.HTTP_SERVER
          errored false
          parent()
          tags {
            "http.url" "http://localhost:$port/"
            "http.method" "GET"
            "span.kind" "server"
            "component" "jetty-handler"
            "span.origin.type" handler.class.name
            "http.status_code" 200
            "peer.ipv4" "127.0.0.1"
            "peer.port" Integer
            defaultTags()
          }
        }
      }
    }
  }

  def "call to jetty with error creates a trace"() {
    setup:
    Handler handler = new AbstractHandler() {
      @Override
      void handle(String target, HttpServletRequest request, HttpServletResponse response, int dispatch) throws IOException, ServletException {
        Request baseRequest = (request instanceof Request) ? (Request) request : HttpConnection.getCurrentConnection().getRequest()
        baseRequest.setHandled(true)

        throw new RuntimeException()
      }
    }
    server.setHandler(handler)
    server.start()
    def request = new okhttp3.Request.Builder()
      .url("http://localhost:$port/")
      .get()
      .build()
    def response = client.newCall(request).execute()

    expect:
    response.body().string().trim() == ""

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          serviceName "unnamed-java-service"
          operationName "jetty.request"
          resourceName "/"
          spanType DDSpanTypes.HTTP_SERVER
          errored true
          parent()
          tags {
            "http.url" "http://localhost:$port/"
            "http.method" "GET"
            "span.kind" "server"
            "component" "jetty-handler"
            "span.origin.type" handler.class.name
            "http.status_code" 200
            "peer.ipv4" "127.0.0.1"
            "peer.port" Integer
            errorTags RuntimeException
            defaultTags()
          }
        }
      }
    }
  }

  def "server-timing traceparent is emitted when configured"() {
    setup:
    def response = null
    withConfigOverride(Config.SERVER_TIMING_CONTEXT, "true") {
      Handler handler = new AbstractHandler() {
        @Override
        void handle(String target, HttpServletRequest request, HttpServletResponse response2, int dispatch) throws IOException, ServletException {
          response2.setContentType("text/plain;charset=utf-8")
          response2.setStatus(HttpServletResponse.SC_OK)

          Request baseRequest = (request instanceof Request) ? (Request) request : HttpConnection.getCurrentConnection().getRequest()
          baseRequest.setHandled(true)
          response2.getWriter().println("Hello World")
        }
      }
      server.setHandler(handler)
      server.start()
      def request = new okhttp3.Request.Builder()
        .url("http://localhost:$port/")
        .get()
        .build()
      response = client.newCall(request).execute()
    }

    expect:
    response.headers().toMultimap().get("Server-Timing").join(',').contains("traceparent")
    response.body().string().trim() == "Hello World"

  }
}
