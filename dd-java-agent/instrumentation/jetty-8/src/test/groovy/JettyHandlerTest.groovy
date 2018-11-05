// Modified by SignalFx
import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.TestUtils
import datadog.trace.agent.test.utils.OkHttpUtils
import okhttp3.OkHttpClient
import org.eclipse.jetty.continuation.Continuation
import org.eclipse.jetty.continuation.ContinuationSupport
import org.eclipse.jetty.server.Handler
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.handler.AbstractHandler

import javax.servlet.ServletException
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class JettyHandlerTest extends AgentTestRunner {

  static {
    System.setProperty("dd.integration.jetty.enabled", "true")
  }

  int port = TestUtils.randomOpenPort()
  Server server = new Server(port)

  OkHttpClient client = OkHttpUtils.client()

  def cleanup() {
    server.stop()
  }

  @Override
  void afterTest() {
  }

  def "call to jetty creates a trace"() {
    setup:
    Handler handler = new AbstractHandler() {
      @Override
      void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        response.setContentType("text/plain;charset=utf-8")
        response.setStatus(HttpServletResponse.SC_OK)
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
    TEST_WRITER.waitForTraces(1)
    TEST_WRITER.size() == 1
    def trace = TEST_WRITER.firstTrace()
    trace.size() == 1
    def span = trace[0]
    span.operationName == "GET ${handler.class.name}"
    span.parentId == 0
    def tags = span.tags()
    tags["http.url"] == "http://localhost:$port/"
    tags["http.method"] == "GET"
    tags["span.kind"] == "server"
    tags["component"] == "jetty-handler"
    tags["http.status_code"] == 200
    tags["span.origin.type"] == handler.class.name
    tags.size() == 7
  }


  def "handler instrumentation clears state after async request"() {
    setup:
    Handler handler = new AbstractHandler() {
      @Override
      void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        final Continuation continuation = ContinuationSupport.getContinuation(request)
        continuation.suspend()
        // By the way, this is a terrible async server
        new Thread() {
          @Override
          void run() {
            continuation.getServletResponse().setContentType("text/plain;charset=utf-8")
            continuation.getServletResponse().getWriter().println("Hello World")
            continuation.complete()
          }
        }.start()

        baseRequest.setHandled(true)
      }
    }
    server.setHandler(handler)
    server.start()
    def request = new okhttp3.Request.Builder()
      .url("http://localhost:$port/")
      .get()
      .build()
    def numTraces = 10
    for (int i = 0; i < numTraces; ++i) {
      assert client.newCall(request).execute().body().string().trim() == "Hello World"
    }

    expect:
    assertTraces(numTraces) {
      for (int i = 0; i < numTraces; ++i) {
        trace(i, 1) {
          span(0) {
            operationName "GET ${handler.class.name}"
          }
        }
      }
    }
  }

  def "call to jetty with error creates a trace"() {
    setup:
    Handler handler = new AbstractHandler() {
      @Override
      void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
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
    TEST_WRITER.waitForTraces(1)
    TEST_WRITER.size() == 1
    def trace = TEST_WRITER.firstTrace()
    trace.size() == 1
    def span = trace[0]
    span.operationName == "GET ${handler.class.name}"
    span.parentId == 0
    def tags = span.tags()
    tags["http.url"] == "http://localhost:$port/"
    tags["http.method"] == "GET"
    tags["span.kind"] == "server"
    tags["component"] == "jetty-handler"
    tags["http.status_code"] == 500
    tags["span.origin.type"] == handler.class.name
    tags["error"] == true
    tags.size() == 8
  }
}
