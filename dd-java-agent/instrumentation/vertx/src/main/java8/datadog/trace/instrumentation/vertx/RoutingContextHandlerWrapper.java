// Modified by SignalFx
package datadog.trace.instrumentation.vertx;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.util.GlobalTracer;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import lombok.extern.slf4j.Slf4j;
import datadog.trace.context.TraceScope;
import static io.opentracing.log.Fields.ERROR_OBJECT;
import io.opentracing.tag.Tags;
import java.util.Collections;

import static datadog.trace.instrumentation.vertx.RoutingContextDecorator.DECORATE;

/**
 * This is used to wrap lambda Handlers since currently we cannot instrument them
 */
@Slf4j
public final class RoutingContextHandlerWrapper implements Handler<RoutingContext> {
  private final Handler handler;

  public RoutingContextHandlerWrapper(final Handler<RoutingContext> handler) {
    this.handler = handler;
  }

  @Override
  public void handle(RoutingContext rc) {
    Scope scope = startSpan(handler, rc);
    try {
      handler.handle(rc);
      stopSpan(null, scope, rc);
    } catch (final Throwable throwable) {
      stopSpan(throwable, scope, rc);
      throw throwable;
    }
  }

  public static Handler<RoutingContext> wrapIfNeeded(final Handler<RoutingContext> handler) {
    // We wrap only lambdas' anonymous classes and if given object has not already been wrapped.
    // Anonymous classes have '/' in class name which is not allowed in 'normal' classes.
    if (handler.getClass().getName().contains("/") && (!(handler instanceof RoutingContextHandlerWrapper))) {
      log.debug("Wrapping handler {}", handler);
      return new RoutingContextHandlerWrapper(handler);
    }
    return handler;
  }

  public static Scope startSpan(
    final Object source, final RoutingContext context) {
    String operationName = source.getClass().getName();
    int indexOfChar = operationName.indexOf("$");
    if (indexOfChar != -1) {
      String auto = "$1234567890";
      if (auto.indexOf(operationName.charAt(indexOfChar+1)) != -1) {
        operationName = operationName.substring(0, indexOfChar);
      }
    }

    final Scope scope =
      GlobalTracer.get()
        .buildSpan(operationName + ".handle")
        .withTag("handler.type", context.getClass().getName())
        .withTag("component", "vertx")
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

  public static void stopSpan(
    final Throwable throwable,
    final Scope scope,
    final RoutingContext context) {
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
