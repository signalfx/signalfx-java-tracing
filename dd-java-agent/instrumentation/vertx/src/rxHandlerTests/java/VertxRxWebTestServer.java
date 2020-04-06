// Modified by SignalFx

import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.SUCCESS;

import com.signalfx.tracing.api.Trace;
import io.reactivex.Single;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.core.http.HttpServerResponse;
import io.vertx.reactivex.ext.jdbc.JDBCClient;
import io.vertx.reactivex.ext.sql.SQLConnection;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.RoutingContext;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class VertxRxWebTestServer extends AbstractVerticle {
  private static final String CONFIG_HTTP_SERVER_PORT = "http.server.port";
  private static JDBCClient client;

  public static Vertx start(final int port) throws ExecutionException, InterruptedException {
    /* This is highly against Vertx ideas, but our tests are synchronous
    so we have to make sure server is up and running */
    final CompletableFuture<Void> future = new CompletableFuture<>();

    final Vertx server = Vertx.vertx(new VertxOptions());

    client =
        JDBCClient.createShared(
            server,
            new JsonObject()
                .put("url", "jdbc:hsqldb:mem:test?shutdown=true")
                .put("driver_class", "org.hsqldb.jdbcDriver"));

    server.deployVerticle(
        VertxRxWebTestServer.class.getName(),
        new DeploymentOptions().setConfig(new JsonObject().put(CONFIG_HTTP_SERVER_PORT, port)),
        res -> {
          if (!res.succeeded()) {
            final RuntimeException exception =
                new RuntimeException("Cannot deploy server Verticle", res.cause());
            future.completeExceptionally(exception);
          }
          future.complete(null);
        });
    // block until vertx server is up
    future.get();

    return server;
  }

  @Override
  public void start(final Future<Void> startFuture) {
    setUpInitialData(
        ready -> {
          final Router router = Router.router(vertx);
          final int port = config().getInteger(CONFIG_HTTP_SERVER_PORT);
          router
              .route(SUCCESS.getPath())
              .handler(
                  ctx -> ctx.response().setStatusCode(SUCCESS.getStatus()).end(SUCCESS.getBody()));

          router.route("/listProducts").handler(this::handleListProducts);

          vertx
              .createHttpServer()
              .requestHandler(router::accept)
              .listen(port, h -> startFuture.complete());
        });
  }

  @Trace
  private void handleListProducts(final RoutingContext routingContext) {
    final HttpServerResponse response = routingContext.response();
    final Single<JsonArray> jsonArraySingle = listProducts();

    jsonArraySingle.subscribe(
        arr -> response.putHeader("content-type", "application/json").end(arr.encode()));
  }

  @Trace
  private Single<JsonArray> listProducts() {
    return client
        .rxQuery("SELECT id, name, price, weight FROM products")
        .flatMap(
            result -> {
              final JsonArray arr = new JsonArray();
              result.getRows().forEach(arr::add);
              return Single.just(arr);
            });
  }

  private void setUpInitialData(final Handler<Void> done) {
    client.getConnection(
        res -> {
          if (res.failed()) {
            throw new RuntimeException(res.cause());
          }

          final SQLConnection conn = res.result();

          conn.execute(
              "CREATE TABLE IF NOT EXISTS products(id INT IDENTITY, name VARCHAR(255), price FLOAT, weight INT)",
              ddl -> {
                if (ddl.failed()) {
                  throw new RuntimeException(ddl.cause());
                }

                conn.execute(
                    "INSERT INTO products (name, price, weight) VALUES ('Egg Whisk', 3.99, 150), ('Tea Cosy', 5.99, 100), ('Spatula', 1.00, 80)",
                    fixtures -> {
                      if (fixtures.failed()) {
                        throw new RuntimeException(fixtures.cause());
                      }

                      done.handle(null);
                    });
              });
        });
  }
}
