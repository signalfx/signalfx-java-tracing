package datadog.trace.instrumentation.jersey;

import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.monitoring.ApplicationEvent;
import org.glassfish.jersey.server.monitoring.ApplicationEventListener;
import org.glassfish.jersey.server.monitoring.RequestEvent;
import org.glassfish.jersey.server.monitoring.RequestEventListener;

public class TracingApplicationEventListener implements ApplicationEventListener {

  @Override
  public void onEvent(ApplicationEvent event) {}

  @Override
  public RequestEventListener onRequest(RequestEvent requestEvent) {
    if (requestEvent.getType() != RequestEvent.Type.START) {
      return null;
    }

    ContainerRequest request = requestEvent.getContainerRequest();
    return new TracingRequestEventListener(request);
  }
}
