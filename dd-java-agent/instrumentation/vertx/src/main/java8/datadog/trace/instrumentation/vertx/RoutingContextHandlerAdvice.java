// Modified by SignalFx
package datadog.trace.instrumentation.vertx;

import io.opentracing.Scope;
import io.vertx.ext.web.RoutingContext;
import net.bytebuddy.asm.Advice;

public class RoutingContextHandlerAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static Scope startSpan(
      @Advice.This final Object source, @Advice.Argument(0) final RoutingContext context) {
    return RoutingContextHandlerWrapper.startSpan(source, context);
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Thrown final Throwable throwable,
      @Advice.Enter final Scope scope,
      @Advice.Argument(0) final RoutingContext context) {
    if (scope != null) {
      RoutingContextHandlerWrapper.stopSpan(throwable, scope, context);
    }
  }
}
