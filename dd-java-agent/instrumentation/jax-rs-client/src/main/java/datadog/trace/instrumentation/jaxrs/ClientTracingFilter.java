// Modified by SignalFx
package datadog.trace.instrumentation.jaxrs;

import datadog.trace.instrumentation.utils.URLUtil;
import io.opentracing.Span;
import io.opentracing.propagation.Format;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import javax.annotation.Priority;
import javax.ws.rs.Priorities;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.client.ClientResponseContext;
import javax.ws.rs.client.ClientResponseFilter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Priority(Priorities.HEADER_DECORATOR)
public class ClientTracingFilter implements ClientRequestFilter, ClientResponseFilter {
  public static final String SPAN_PROPERTY_NAME = "datadog.trace.jax-rs-client.span";
  public static final String SPAN_HAS_ERRORED = "ot.trace.jax-rs-client.spanHasErrored";

  @Override
  public void filter(final ClientRequestContext requestContext) {

    final Span span =
        GlobalTracer.get()
            .buildSpan(
                URLUtil.deriveOperationName(
                    requestContext.getMethod(), requestContext.getUri().toString()))
            .withTag(Tags.COMPONENT.getKey(), "jax-rs.client")
            .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
            .withTag(Tags.HTTP_METHOD.getKey(), requestContext.getMethod())
            .withTag(Tags.HTTP_URL.getKey(), requestContext.getUri().toString())
            .start();

    log.debug("{} - client span started", span);

    GlobalTracer.get()
        .inject(
            span.context(),
            Format.Builtin.HTTP_HEADERS,
            new InjectAdapter(requestContext.getHeaders()));

    requestContext.setProperty(SPAN_PROPERTY_NAME, span);
    requestContext.setProperty(SPAN_HAS_ERRORED, false);
  }

  @Override
  public void filter(
      final ClientRequestContext requestContext, final ClientResponseContext responseContext) {
    final Object spanObj = requestContext.getProperty(SPAN_PROPERTY_NAME);
    if (spanObj instanceof Span) {
      final Span span = (Span) spanObj;
      Tags.HTTP_STATUS.set(span, responseContext.getStatus());

      span.finish();
      log.debug("{} - client spanObj finished", spanObj);
    }
  }
}
