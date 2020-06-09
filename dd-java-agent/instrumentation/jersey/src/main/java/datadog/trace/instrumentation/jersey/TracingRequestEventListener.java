// Modified by SignalFx
package datadog.trace.instrumentation.jersey;

import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activeSpan;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.propagate;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.startSpan;
import static datadog.trace.instrumentation.jersey.JerseyRequestExtractAdapter.GETTER;

import datadog.trace.bootstrap.instrumentation.api.AgentScope;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan.Context;
import datadog.trace.bootstrap.instrumentation.api.Tags;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.ContainerResponse;
import org.glassfish.jersey.server.monitoring.RequestEvent;
import org.glassfish.jersey.server.monitoring.RequestEventListener;

public class TracingRequestEventListener implements RequestEventListener {
  private AgentScope scope;

  public TracingRequestEventListener(ContainerRequest request) {
    AgentSpan parentSpan = activeSpan();

    boolean setKind = false;
    Context parentContext;
    if (parentSpan != null) {
      // Instrumented servers will have established context
      parentContext = parentSpan.context();
    } else {
      parentContext = propagate().extract(request, GETTER);
      setKind = true;
    }

    final AgentSpan span =
        startSpan("jersey.request", parentContext)
            .setTag(Tags.COMPONENT, "jersey")
            .setTag(Tags.HTTP_METHOD, request.getMethod())
            .setTag(Tags.HTTP_URL, request.getRequestUri().toString());

    if (setKind) {
      span.setTag(Tags.SPAN_KIND, Tags.SPAN_KIND_SERVER);
    }

    scope = activateSpan(span, false);
    scope.setAsyncPropagation(true);
  }

  @Override
  public void onEvent(RequestEvent event) {
    final AgentSpan span = scope.span();

    switch (event.getType()) {
      case ON_EXCEPTION:
        span.setTag(Tags.ERROR, true);
        span.addThrowable(event.getException());
        break;
      case FINISHED:
        ContainerResponse response = event.getContainerResponse();
        // Unmapped exceptions are rethrown to the container
        // and responses, if any, are created there.
        if (response != null) {
          span.setTag(Tags.HTTP_STATUS, response.getStatus());
        }

        scope.close();
        span.finish();
    }
  }
}
