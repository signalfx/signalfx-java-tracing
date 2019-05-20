package datadog.trace.instrumentation.jersey;

import static io.opentracing.log.Fields.ERROR_OBJECT;

import datadog.trace.context.TraceScope;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.util.Collections;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.ContainerResponse;
import org.glassfish.jersey.server.monitoring.RequestEvent;
import org.glassfish.jersey.server.monitoring.RequestEventListener;

public class TracingRequestEventListener implements RequestEventListener {
  private Scope scope;

  public TracingRequestEventListener(ContainerRequest request) {
    Tracer tracer = GlobalTracer.get();

    Scope parentScope = tracer.scopeManager().active();
    SpanContext parentContext;
    if (parentScope != null) {
      // Instrumented servers will have established context
      parentContext = parentScope.span().context();
    } else {
      parentContext =
          tracer.extract(Format.Builtin.HTTP_HEADERS, new JerseyRequestExtractAdapter(request));
    }

    scope =
        tracer
            .buildSpan("jersey.request")
            .asChildOf(parentContext)
            .withTag(Tags.COMPONENT.getKey(), "jersey")
            .withTag(Tags.HTTP_METHOD.getKey(), request.getMethod())
            .withTag(Tags.HTTP_URL.getKey(), request.getRequestUri().toString())
            .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER)
            .startActive(false);

    if (scope instanceof TraceScope) {
      ((TraceScope) scope).setAsyncPropagation(true);
    }
  }

  @Override
  public void onEvent(RequestEvent event) {
    final Span span = scope.span();

    switch (event.getType()) {
      case ON_EXCEPTION:
        Tags.ERROR.set(span, Boolean.TRUE);
        span.log(Collections.singletonMap(ERROR_OBJECT, event.getException()));
        break;
      case FINISHED:
        ContainerResponse response = event.getContainerResponse();
        // Unmapped exceptions are rethrown to the container
        // and responses, if any, are created there.
        if (response != null) {
          Tags.HTTP_STATUS.set(span, response.getStatus());
        }

        scope.close();
        span.finish();
    }
  }
}
