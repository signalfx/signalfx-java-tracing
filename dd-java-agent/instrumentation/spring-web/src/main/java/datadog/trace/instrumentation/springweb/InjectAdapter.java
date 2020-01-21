// Modified by SignalFx
package datadog.trace.instrumentation.springweb;

import datadog.trace.instrumentation.api.AgentPropagation;
import org.springframework.http.HttpHeaders;

public class InjectAdapter implements AgentPropagation.Setter<HttpHeaders> {

  public static final InjectAdapter SETTER = new InjectAdapter();

  @Override
  public void set(final HttpHeaders carrier, final String key, final String value) {
    carrier.set(key, value);
  }
}
