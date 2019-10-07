// Modified by SignalFx
package datadog.trace.instrumentation.vertx;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import net.bytebuddy.asm.Advice;

public class RouteAdvice {
  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void wrapHandler(@Advice.Argument(value = 0, readOnly = false) Handler<RoutingContext> handler) {
    handler = RoutingContextHandlerWrapper.wrapIfNeeded(handler);
  }
}
