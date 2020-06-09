// Modified by SignalFx
package datadog.trace.instrumentation.jetty6;

import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.propagate;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.startSpan;
import static datadog.trace.bootstrap.instrumentation.decorator.HttpServerDecorator.DD_SPAN_ATTRIBUTE;
import static datadog.trace.instrumentation.jetty6.HttpServletRequestExtractAdapter.GETTER;
import static datadog.trace.instrumentation.jetty6.JettyDecorator.DECORATE;

import datadog.trace.api.DDTags;
import datadog.trace.bootstrap.instrumentation.api.AgentScope;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.bytebuddy.asm.Advice;

public class JettyHandlerAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static AgentScope onEnter(
      @Advice.This final Object source, @Advice.Argument(1) final HttpServletRequest req) {

    if (req.getAttribute(DD_SPAN_ATTRIBUTE) != null) {
      // Request already being traced elsewhere.
      return null;
    }

    final AgentSpan.Context extractedContext = propagate().extract(req, GETTER);

    final AgentSpan span =
        startSpan("jetty.request", extractedContext)
            .setTag("span.origin.type", source.getClass().getName());

    final String resourceName = source.getClass().getName();
    span.setTag(DDTags.RESOURCE_NAME, resourceName);

    DECORATE.afterStart(span);
    DECORATE.onConnection(span, req);
    // http url must be set after resource name for URLAsResourceName decorator
    DECORATE.onRequest(span, req);

    final AgentScope scope = activateSpan(span, false);
    scope.setAsyncPropagation(true);
    req.setAttribute(DD_SPAN_ATTRIBUTE, span);
    return scope;
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Argument(1) final HttpServletRequest req,
      @Advice.Argument(2) final HttpServletResponse resp,
      @Advice.Enter final AgentScope scope,
      @Advice.Thrown final Throwable throwable) {

    if (scope == null) {
      return;
    }

    final AgentSpan span = scope.span();

    if (req.getUserPrincipal() != null) {
      span.setTag(DDTags.USER_NAME, req.getUserPrincipal().getName());
    }

    DECORATE.onResponse(span, resp);

    if (throwable != null) {
      DECORATE.onError(span, throwable);
    }

    DECORATE.beforeFinish(span);
    scope.close();
    span.finish(); // Finish the span manually since finishSpanOnClose was false
  }
}
