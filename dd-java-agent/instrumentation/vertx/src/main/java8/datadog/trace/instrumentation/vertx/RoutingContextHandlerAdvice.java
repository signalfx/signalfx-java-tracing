// Modified by SignalFx
package datadog.trace.instrumentation.vertx;

import static datadog.trace.instrumentation.vertx.RoutingContextDecorator.DECORATE;

import datadog.trace.context.TraceScope;
import static io.opentracing.log.Fields.ERROR_OBJECT;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import io.vertx.ext.web.RoutingContext;
import java.util.Collections;
import net.bytebuddy.asm.Advice;

public class RoutingContextHandlerAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static Scope startSpan(
      @Advice.This final Object source, @Advice.Argument(0) final RoutingContext context) {
    final Scope scope =
        GlobalTracer.get()
            .buildSpan(source.getClass().getName() + ".handle")
            .withTag("handler.type", context.getClass().getName())
            .withTag("component", "vertx")
            .startActive(true);

    final Span span = scope.span();
    DECORATE.afterStart(span);
    DECORATE.onConnection(span, context.request());
    DECORATE.onRequest(span, context.request());

    if (scope instanceof TraceScope) {
      ((TraceScope) scope).setAsyncPropagation(true);
    }

    return scope;
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Thrown final Throwable throwable,
      @Advice.Enter final Scope scope,
      @Advice.Argument(0) final RoutingContext context) {
    if (scope != null) {
      final Span span = scope.span();
      DECORATE.onResponse(span, context.response());

      if (throwable != null) {
        Tags.ERROR.set(span, true);
        DECORATE.onError(span, throwable);
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
