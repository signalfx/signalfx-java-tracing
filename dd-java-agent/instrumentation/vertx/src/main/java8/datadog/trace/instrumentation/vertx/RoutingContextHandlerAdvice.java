// Modified by SignalFx
package datadog.trace.instrumentation.vertx;

import datadog.trace.instrumentation.api.AgentScope;
import io.vertx.ext.web.RoutingContext;
import net.bytebuddy.asm.Advice;

public class RoutingContextHandlerAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static AgentScope startSpan(
      @Advice.This final Object source, @Advice.Argument(0) final RoutingContext context) {
    return RoutingContextHandlerWrapper.startAndActivate(source, context);
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Thrown final Throwable throwable,
      @Advice.Enter final AgentScope scope,
      @Advice.Argument(0) final RoutingContext context) {
    if (scope != null) {
      RoutingContextHandlerWrapper.stopAndClose(throwable, scope, context);
    }
  }
}
