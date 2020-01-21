// Modified by SignalFx
package datadog.trace.instrumentation.vertx;

import static datadog.trace.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.instrumentation.api.AgentTracer.startSpan;

import datadog.trace.instrumentation.api.AgentScope;
import datadog.trace.instrumentation.api.AgentSpan;
import datadog.trace.instrumentation.api.Tags;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import lombok.extern.slf4j.Slf4j;
import datadog.trace.context.TraceScope;
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
    AgentScope scope = startAndActivate(handler, rc);
    try {
      handler.handle(rc);
      stopAndClose(null, scope, rc);
    } catch (final Throwable throwable) {
      stopAndClose(throwable, scope, rc);
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

  public static AgentScope startAndActivate(
    final Object source, final RoutingContext context) {
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
        .setTag("handler.type", context.getClass().getName())
        .setTag("component", "vertx");

    DECORATE.afterStart(span);
    DECORATE.onConnection(span, context.request());
    DECORATE.onRequest(span, context.request());

    final AgentScope scope = activateSpan(span, false);
    scope.setAsyncPropagation(true);
    return scope;
  }

  public static void stopAndClose(
    final Throwable throwable,
    final AgentScope scope,
    final RoutingContext context) {
    if (scope != null) {
      final AgentSpan span = scope.span();
      DECORATE.onResponse(span, context.response());

      if (throwable != null) {
        span.setTag(Tags.ERROR, true);
        DECORATE.onError(span, throwable);
        span.addThrowable(throwable);
      }

      scope.setAsyncPropagation(false);

      DECORATE.beforeFinish(span);
      span.finish();
      scope.close();
    }
  }
}
