package datadog.trace.instrumentation.vertx;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static io.opentracing.log.Fields.ERROR_OBJECT;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.util.Collections;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public final class HandlerInstrumentation extends Instrumenter.Default {

  public HandlerInstrumentation() {
    super("vertx", "vertx");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return not(isInterface()).and(safeHasSuperType(named("io.vertx.core.Handler")));
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod()
            .and(named("handle"))
            // add argument classes to exclude here for specific types of handlers
            .and(not(takesArgument(0, named("io.vertx.ext.web.RoutingContext")))),
        VertxHandlerAdvice.class.getName());
  }

  public static class VertxHandlerAdvice {

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
}
