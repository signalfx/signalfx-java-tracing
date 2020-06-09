// Modified by SignalFx
package datadog.trace.instrumentation.khttp;

import datadog.trace.bootstrap.instrumentation.api.AgentPropagation;
import java.util.Iterator;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class KHttpHeadersInjectAdapter implements AgentPropagation.Setter<Map> {

  public static final KHttpHeadersInjectAdapter SETTER = new KHttpHeadersInjectAdapter();

  @Override
  public void set(final Map carrier, final String key, final String value) {
    try {
      carrier.put(key, value);
    } catch (final java.lang.UnsupportedOperationException e) {
      log.debug("Unable to propagate trace context via KHttp request headers: " + e.toString());
    }
  }
}
