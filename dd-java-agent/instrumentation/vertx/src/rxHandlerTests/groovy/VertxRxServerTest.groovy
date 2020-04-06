// Modified by SignalFx


import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.utils.OkHttpUtils
import datadog.trace.agent.test.utils.PortUtils
import io.vertx.reactivex.core.Vertx
import okhttp3.OkHttpClient
import okhttp3.Request
import spock.lang.Shared

import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.SUCCESS

class VertxRxServerTest extends AgentTestRunner {
  static {
    System.setProperty("dd.integration.jdbc.enabled", "true")
  }

  @Shared
  OkHttpClient client = OkHttpUtils.client()

  @Shared
  int port

  @Shared
  Vertx server

  def setupSpec() {
    port = PortUtils.randomOpenPort()
    server = VertxRxWebTestServer.start(port)
  }

  def cleanupSpec() {
    server.close()
  }

  def "test #responseCode response handling"() {
    setup:
    def request = new Request.Builder().url("http://localhost:$port$path").get().build()
    def response = client.newCall(request).execute()

    expect:
    response.code() == responseCode

    and:
    assertTraces(1) {
      trace(0, 5) {
        spanByOperationName("netty.request") {
          spanType "web"
        }
        spanByOperationName("io.vertx.core.http.impl.WebSocketRequestHandler.handle") {
          childOf spanByOperationName("netty.request")
        }
        spanByOperationName("io.vertx.reactivex.core.http.HttpServer.handle") {
          childOf spanByOperationName("io.vertx.core.http.impl.WebSocketRequestHandler.handle")
        }
        spanByOperationName("io.vertx.ext.web.impl.RouterImpl.handle") {
          childOf spanByOperationName("io.vertx.reactivex.core.http.HttpServer.handle")
        }
        spanByOperationName("io.vertx.reactivex.ext.web.Route.handle") {
          childOf spanByOperationName("io.vertx.ext.web.impl.RouterImpl.handle")
        }
      }
    }

    where:
    responseCode   | path
    SUCCESS.status | SUCCESS.path
  }

  def "test causality for jdbc rx"() {
    setup:
    def request = new Request.Builder().url("http://localhost:$port$path").get().build()
    def response = client.newCall(request).execute()

    expect:
    response.code() == responseCode

    and:
    assertTraces(1) {
      trace(0, 9) {
        spanByOperationName("netty.request") {
          spanType "web"
        }
        spanByOperationName("io.vertx.core.http.impl.WebSocketRequestHandler.handle") {
          childOf spanByOperationName("netty.request")
        }
        spanByOperationName("io.vertx.reactivex.core.http.HttpServer.handle") {
          childOf spanByOperationName("io.vertx.core.http.impl.WebSocketRequestHandler.handle")
        }
        spanByOperationName("io.vertx.ext.web.impl.RouterImpl.handle") {
          childOf spanByOperationName("io.vertx.reactivex.core.http.HttpServer.handle")
        }
        spanByOperationName("io.vertx.reactivex.ext.web.Route.handle") {
          childOf spanByOperationName("io.vertx.ext.web.impl.RouterImpl.handle")
        }
        spanByOperationName("VertxRxWebTestServer.handleListProducts") {
          childOf spanByOperationName("io.vertx.reactivex.ext.web.Route.handle")
        }
        spanByOperationName("VertxRxWebTestServer.listProducts") {
          childOf spanByOperationName("VertxRxWebTestServer.handleListProducts")
        }
        spanByOperationName("hsqldb.query") {
          spanType "sql"
          childOf spanByOperationName("VertxRxWebTestServer.listProducts")
        }
        spanByOperationName("database.connection") {
          childOf spanByOperationName("VertxRxWebTestServer.listProducts")
        }
      }
    }

    where:
    responseCode   | name            | path            | error
    SUCCESS.status | "/listProducts" | "/listProducts" | false
  }


}
