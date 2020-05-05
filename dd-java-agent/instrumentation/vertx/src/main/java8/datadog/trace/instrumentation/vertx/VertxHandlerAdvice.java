// Modified by SignalFx
package datadog.trace.instrumentation.vertx;

import static datadog.trace.instrumentation.api.AgentTracer.activeSpan;
import static datadog.trace.instrumentation.vertx.RoutingContextDecorator.DECORATE;
import static datadog.trace.instrumentation.api.AgentTracer.startSpan;
import static datadog.trace.instrumentation.api.AgentTracer.activateSpan;

import datadog.trace.context.TraceScope;
import datadog.trace.instrumentation.api.AgentScope;
import datadog.trace.instrumentation.api.AgentSpan;
import datadog.trace.instrumentation.api.Tags;
import java.util.Collections;
import net.bytebuddy.asm.Advice;

public class VertxHandlerAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static AgentScope onEnter(
      @Advice.This final Object source, @Advice.Argument(0) final Object event) {

    // Vertx timers (periodic or one-shot), when created outside any trace context,
    // should not create a trace.
    if (source.getClass().getName().startsWith("io.vertx.core")
        && source.getClass().getName().endsWith("InternalTimerHandler")
        && activeSpan() == null) {
      return null;
    }

    String operationName = source.getClass().getName();

    int indexOfChar = operationName.lastIndexOf('$');
    if (indexOfChar != -1) {
      String auto = "1234567890";
      if (auto.indexOf(operationName.charAt(indexOfChar+1)) != -1) {
        operationName = operationName.substring(0, indexOfChar);
      }
    }

    final AgentSpan span =
        startSpan(operationName + ".handle")
        .setTag("component", "vertx");

    if (event != null) {
      span.setTag("handler.type", event.getClass().getName());
    }

    DECORATE.afterStart(span);

    final AgentScope scope = activateSpan(span, false);
    scope.setAsyncPropagation(true);
    return scope;
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Thrown final Throwable throwable,
      @Advice.Enter final AgentScope scope,
      @Advice.This final Object source) {
    if (scope != null) {
      final AgentSpan span = scope.span();
      if (throwable != null) {
        span.setTag(Tags.ERROR, true);
        span.addThrowable(throwable);
      }
      DECORATE.beforeFinish(span);
      scope.setAsyncPropagation(false);
      span.finish();
      scope.close();
    }
  }
}
