package datadog.trace.instrumentation.vertx;

import static io.opentracing.log.Fields.ERROR_OBJECT;

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
    return GlobalTracer.get()
        .buildSpan(source.getClass().getName() + ".handle")
        .withTag("handler.type", event.getClass().getName())
        .withTag("component", "vertx")
        .startActive(true);
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

      scope.close();
    }
  }
}
