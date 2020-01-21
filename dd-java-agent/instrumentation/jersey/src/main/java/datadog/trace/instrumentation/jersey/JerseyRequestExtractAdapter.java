// Modified by SignalFx
package datadog.trace.instrumentation.jersey;

import datadog.trace.instrumentation.api.AgentPropagation;
import java.util.List;
import javax.ws.rs.core.MultivaluedMap;
import org.glassfish.jersey.server.ContainerRequest;

public class JerseyRequestExtractAdapter implements AgentPropagation.Getter<ContainerRequest> {
  public static final JerseyRequestExtractAdapter GETTER = new JerseyRequestExtractAdapter();

  @Override
  public Iterable<String> keys(final ContainerRequest request) {
    final MultivaluedMap<String, String> headers = request.getRequestHeaders();
    return headers.keySet();
  }

  @Override
  public String get(final ContainerRequest request, final String key) {
    final List<String> header = request.getRequestHeader(key);
    if (header == null || header.isEmpty()) {
      return null;
    }
    return header.get(0);
  }
}
