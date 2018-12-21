// Modified by SignalFx
import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.utils.OkHttpUtils
import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.eclipse.jetty.http.HttpHeaders
import org.eclipse.jetty.http.security.Constraint
import org.eclipse.jetty.security.ConstraintMapping
import org.eclipse.jetty.security.ConstraintSecurityHandler
import org.eclipse.jetty.security.HashLoginService
import org.eclipse.jetty.security.LoginService
import org.eclipse.jetty.security.authentication.BasicAuthenticator
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.ServletContextHandler

class JettyServlet2Test extends AgentTestRunner {

  OkHttpClient client = OkHttpUtils.clientBuilder().addNetworkInterceptor(new Interceptor() {
    @Override
    Response intercept(Interceptor.Chain chain) throws IOException {
      def response = chain.proceed(chain.request())
      TEST_WRITER.waitForTraces(1)
      return response
    }
  })
    .build()

  int port
  private Server jettyServer
  private ServletContextHandler servletContext

  def setup() {
    jettyServer = new Server(0)
    servletContext = new ServletContextHandler()
    servletContext.contextPath = "/ctx"

    ConstraintSecurityHandler security = setupAuthentication(jettyServer)

    servletContext.setSecurityHandler(security)
    servletContext.addServlet(TestServlet2.Sync, "/sync")
    servletContext.addServlet(TestServlet2.Sync, "/auth/sync")

    jettyServer.setHandler(servletContext)
    jettyServer.start()
    port = jettyServer.connectors[0].localPort
  }

  def cleanup() {
    jettyServer.stop()
    jettyServer.destroy()
  }

  def "test #path servlet call (auth: #auth, distributed tracing: #distributedTracing)"() {
    setup:
    def requestBuilder = new Request.Builder()
      .url("http://localhost:$port/ctx/$path")
      .get()
    if (distributedTracing) {
      requestBuilder.header("traceid", tid.toString())
      requestBuilder.header("spanid", "0")
    }
    if (auth) {
      requestBuilder.header(HttpHeaders.AUTHORIZATION, Credentials.basic("user", "password"))
    }
    def response = client.newCall(requestBuilder.build()).execute()

    expect:
    response.body().string().trim() == expectedResponse

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          if (distributedTracing) {
            traceId tid
            parentId 0
          } else {
            parent()
          }
          operationName "GET /ctx/$path"
          errored false
          tags {
            "http.url" "http://localhost:$port/ctx/$path"
            "http.method" "GET"
            "span.kind" "server"
            "component" "java-web-servlet"
            "span.origin.type" "TestServlet2\$Sync"
            "servlet.context" "/ctx"
            if (auth) {
              "user.name" "user"
            }
            defaultTags(distributedTracing)
          }
        }
      }
    }

    where:
    path        | expectedResponse | auth  | distributedTracing | tid
    "sync"      | "Hello Sync"     | false | false              | 123
    "auth/sync" | "Hello Sync"     | true  | false              | 124
    "sync"      | "Hello Sync"     | false | true               | 125
    "auth/sync" | "Hello Sync"     | true  | true               | 126
  }

  def "test #path error servlet call"() {
    setup:
    def request = new Request.Builder()
      .url("http://localhost:$port/ctx/$path?error=true")
      .get()
      .build()
    def response = client.newCall(request).execute()

    expect:
    response.body().string().trim() != expectedResponse

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          operationName "GET /ctx/$path"
          errored true
          parent()
          tags {
            "http.url" "http://localhost:$port/ctx/$path"
            "http.method" "GET"
            "span.kind" "server"
            "component" "java-web-servlet"
            "span.origin.type" "TestServlet2\$Sync"
            "servlet.context" "/ctx"
            errorTags(RuntimeException, "some $path error")
            defaultTags()
          }
        }
      }
    }

    where:
    path   | expectedResponse
    "sync" | "Hello Sync"
  }

  def "test #path non-throwing-error servlet call"() {
    // This doesn't actually detect the error because we can't get the status code via the old servlet API.
    setup:
    def request = new Request.Builder()
      .url("http://localhost:$port/ctx/$path?non-throwing-error=true")
      .get()
      .build()
    def response = client.newCall(request).execute()

    expect:
    response.body().string().trim() != expectedResponse

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          operationName "GET /ctx/$path"
          errored false
          parent()
          tags {
            "http.url" "http://localhost:$port/ctx/$path"
            "http.method" "GET"
            "span.kind" "server"
            "component" "java-web-servlet"
            "span.origin.type" "TestServlet2\$Sync"
            "servlet.context" "/ctx"
            defaultTags()
          }
        }
      }
    }

    where:
    path   | expectedResponse
    "sync" | "Hello Sync"
  }

  /**
   * Setup simple authentication for tests
   * <p>
   *     requests to {@code /auth/*} need login 'user' and password 'password'
   * <p>
   *     For details @see <a href="http://www.eclipse.org/jetty/documentation/9.3.x/embedded-examples.html">http://www.eclipse.org/jetty/documentation/9.3.x/embedded-examples.html</a>
   *
   * @param jettyServer server to attach login service
   * @return SecurityHandler that can be assigned to servlet
   */
  private ConstraintSecurityHandler setupAuthentication(Server jettyServer) {
    ConstraintSecurityHandler security = new ConstraintSecurityHandler()

    Constraint constraint = new Constraint()
    constraint.setName("auth")
    constraint.setAuthenticate(true)
    constraint.setRoles("role")

    ConstraintMapping mapping = new ConstraintMapping()
    mapping.setPathSpec("/auth/*")
    mapping.setConstraint(constraint)

    security.setConstraintMappings(mapping)
    security.setAuthenticator(new BasicAuthenticator())

    LoginService loginService = new HashLoginService("TestRealm",
      "src/test/resources/realm.properties")
    security.setLoginService(loginService)
    jettyServer.addBean(loginService)

    security
  }
}
