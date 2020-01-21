// Modified by SignalFx
package server;

import com.signalfx.tracing.api.Trace;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class VertxWebTestServer extends AbstractVerticle {

  class MyHandler implements Handler<RoutingContext> {
    public void handle(RoutingContext routingContext) {
      tracedMethod();
      routingContext.next();
    }
  }

  public static Vertx start(final int port) throws ExecutionException, InterruptedException {
    /* This is highly against Vertx ideas, but our tests are synchronous
    so we have to make sure server is up and running */
    final CompletableFuture<Void> future = new CompletableFuture<>();

    final Vertx vertx = Vertx.vertx(new VertxOptions());
    vertx.deployVerticle(
        new VertxWebTestServer(port),
        res -> {
          if (!res.succeeded()) {
            throw new RuntimeException("Cannot deploy server Verticle");
          }
          future.complete(null);
        });

    future.get();

    return vertx;
  }

  private final int port;

  public VertxWebTestServer(final int port) {
    this.port = port;
  }

  @Override
  public void start(final Future<Void> startFuture) {
    final Router router = Router.router(vertx);

    router
        .route("/")
        .handler(
            routingContext -> {
              routingContext.response().putHeader("content-type", "text/html").end("Hello World");
            });
    router
        .route("/error")
        .handler(
            routingContext -> {
              routingContext.response().setStatusCode(500).end();
            });
    router
        .route("/test")
        .handler(
            routingContext -> {
              tracedMethod();
              routingContext.next();
            });
    router.route("/test").blockingHandler(new MyHandler());
    router
        .route("/test")
        .handler(
            routingContext -> {
              routingContext.response().putHeader("content-type", "text/html").end("Hello World");
            });
    router
        .route("/test/post")
        .handler(
            routingContext -> {
              routingContext
                  .response()
                  .setStatusCode(201)
                  .putHeader("content-type", "application/json; charset=utf-8")
                  .end("Testing post");
            });

    vertx
        .createHttpServer()
        .requestHandler(router::accept)
        .listen(port, h -> startFuture.complete());
  }

  @Trace
  public void tracedMethod() {}
}
