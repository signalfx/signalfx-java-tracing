// Modified by SignalFx
package datadog.trace.instrumentation.jetty6;

import static datadog.trace.instrumentation.jetty6.JettyDecorator.DECORATE;

import datadog.trace.api.DDTags;
import datadog.trace.context.TraceScope;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.propagation.Format;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.bytebuddy.asm.Advice;
import org.mortbay.jetty.HttpConnection;
import org.mortbay.jetty.Response;

public class JettyHandlerAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static Scope startSpan(
      @Advice.This final Object source, @Advice.Argument(1) final HttpServletRequest req) {

    if (GlobalTracer.get().activeSpan() != null) {
      // Tracing might already be applied.  If so ignore this.
      return null;
    }

    final SpanContext extractedContext =
        GlobalTracer.get()
            .extract(Format.Builtin.HTTP_HEADERS, new HttpServletRequestExtractAdapter(req));
    final Scope scope =
        GlobalTracer.get()
            .buildSpan("jetty.request")
            .asChildOf(extractedContext)
            .withTag("span.origin.type", source.getClass().getName())
            .startActive(false);

    final Span span = scope.span();
    final String resourceName = source.getClass().getName();
    span.setTag(DDTags.RESOURCE_NAME, resourceName);

    DECORATE.afterStart(span);
    DECORATE.onConnection(span, req);
    // http url must be set after resource name for URLAsResourceName decorator
    DECORATE.onRequest(span, req);

    if (scope instanceof TraceScope) {
      ((TraceScope) scope).setAsyncPropagation(true);
    }
    return scope;
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Argument(1) final HttpServletRequest req,
      @Advice.Argument(2) final HttpServletResponse resp,
      @Advice.Enter final Scope scope,
      @Advice.Thrown final Throwable throwable) {

    if (scope != null) {
      final Span span = scope.span();

      if (req.getUserPrincipal() != null) {
        span.setTag(DDTags.USER_NAME, req.getUserPrincipal().getName());
      }

      Response response = HttpConnection.getCurrentConnection().getResponse();
      DECORATE.onResponse(span, resp);

      if (throwable != null) {
        DECORATE.onError(span, throwable);
      } else {
        Tags.HTTP_STATUS.set(span, response.getStatus());
      }

      if (scope instanceof TraceScope) {
        ((TraceScope) scope).setAsyncPropagation(false);
      }

      DECORATE.beforeFinish(span);
      scope.close();
      span.finish(); // Finish the span manually since finishSpanOnClose was false
    }
  }
}
