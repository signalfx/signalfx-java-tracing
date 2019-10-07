// Modified by SignalFx
package datadog.trace.instrumentation.vertx;

import static datadog.trace.instrumentation.vertx.RoutingContextDecorator.DECORATE;
import static io.opentracing.log.Fields.ERROR_OBJECT;

import datadog.trace.context.TraceScope;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.util.Collections;
import net.bytebuddy.asm.Advice;

public class VertxHandlerAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static Scope startSpan(
      @Advice.This final Object source, @Advice.Argument(0) final Object event) {

    String operationName = source.getClass().getName();
    int indexOfChar = operationName.indexOf('$');
    operationName = operationName.substring(0, indexOfChar);

    Scope  scope = GlobalTracer.get()
        .buildSpan(operationName + ".handle")
        .withTag("handler.type", event.getClass().getName())
        .withTag("component", "vertx")
        .startActive(true);

    final Span span = scope.span();
    DECORATE.afterStart(span);
    if (scope instanceof TraceScope) {
      ((TraceScope) scope).setAsyncPropagation(true);
    }
    return scope;
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Thrown final Throwable throwable,
      @Advice.Enter final Scope scope,
      @Advice.This final Object source) {
    if (scope != null) {
      final Span span = scope.span();
      if (throwable != null) {
        Tags.ERROR.set(span, true);
        span.log(Collections.singletonMap(ERROR_OBJECT, throwable));
      }
      if (scope instanceof TraceScope) {
        ((TraceScope) scope).setAsyncPropagation(false);
      }
      DECORATE.beforeFinish(span);
      span.finish();
      scope.close();
    }
  }
}
