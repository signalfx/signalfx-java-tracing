package datadog.trace.instrumentation.vertx;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static datadog.trace.instrumentation.vertx.RoutingContextDecorator.DECORATE;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.context.TraceScope;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.util.GlobalTracer;
import io.vertx.ext.web.RoutingContext;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public final class ContextHandlerInstrumentation extends Instrumenter.Default {

  public ContextHandlerInstrumentation() {
    super("vertx", "vertx");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return not(isInterface()).and(safeHasSuperType(named("io.vertx.core.Handler")));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "datadog.trace.agent.decorator.BaseDecorator",
      "datadog.trace.agent.decorator.ServerDecorator",
      "datadog.trace.agent.decorator.HttpServerDecorator",
      packageName + ".RoutingContextDecorator",
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod()
            .and(named("handle"))
            .and(takesArgument(0, named("io.vertx.ext.web.RoutingContext"))),
        ContextVertxHandlerAdvice.class.getName());
  }

  public static class ContextVertxHandlerAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Scope startSpan(
        @Advice.This final Object source, @Advice.Argument(0) final RoutingContext context) {
      final Scope scope =
          GlobalTracer.get()
              .buildSpan(source.getClass().getName() + ".handle")
              .withTag("handler.type", context.getClass().getName())
              .startActive(false);

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

        if (scope instanceof TraceScope) {
          ((TraceScope) scope).setAsyncPropagation(false);
        }

        span.finish();
        scope.close();
      }
    }
  }
}
