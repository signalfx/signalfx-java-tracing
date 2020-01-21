// Modified by SignalFx
import com.signalfx.tracing.api.Trace
import io.vertx.core.AbstractVerticle
import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.core.VertxOptions
import io.vertx.ext.web.Router
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException

class VertxWebTestServer(private val port:Int):AbstractVerticle() {

  override fun start(startFuture:Future<Void>) {
    val router = Router.router(vertx)

    router
    .route("/")
    .handler(
      { routingContext-> routingContext.response().putHeader("content-type", "text/html").end("Hello World") })

    router
    .route("/error")
    .handler(
      { routingContext-> routingContext.response().setStatusCode(500).end() })

    router
    .route("/test")
    .handler(
      { routingContext->
       tracedMethod()
       routingContext.next() })
    .blockingHandler(
      { routingContext-> routingContext.next() })
    .handler(
      { routingContext-> routingContext.response().putHeader("content-type", "text/html").end("Hello World") })

    vertx
    .createHttpServer()
    .requestHandler { router.accept(it) }
    .listen(port) { h ->
      if (h.succeeded()) {
        startFuture.complete()
      } else {
        startFuture.fail(h.cause())
      }
    }
  }

  @Trace
  fun tracedMethod() {}

  // mimic static method
  companion object {

    @Throws(ExecutionException::class, InterruptedException::class)
    @JvmStatic
    fun start(port:Int):Vertx {
      /* This is highly against Vertx ideas, but our tests are synchronous
 so we have to make sure server is up and running */
      val future = CompletableFuture<Void>()
      val vertx = Vertx.vertx(VertxOptions())

      vertx.deployVerticle(
        VertxWebTestServer(port),
        { res->
         if (!res.succeeded())
         {
           throw RuntimeException("Cannot deploy server Verticle")
         }
         future.complete(null) })

      future.get()
      return vertx
    }
  }
}
