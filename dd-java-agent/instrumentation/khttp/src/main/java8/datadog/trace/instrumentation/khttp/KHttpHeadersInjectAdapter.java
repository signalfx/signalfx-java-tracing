// Modified by SignalFx
package datadog.trace.instrumentation.khttp;

import io.opentracing.propagation.TextMap;
import java.util.Iterator;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class KHttpHeadersInjectAdapter implements TextMap {

  private final Map headers;

  public KHttpHeadersInjectAdapter(final Map headers) {
    this.headers = headers;
  }

  @Override
  public void put(final String key, final String value) {
    try {
      headers.put(key, value);
    } catch (final java.lang.UnsupportedOperationException e) {
      log.debug("Unable to propagate trace context via KHttp request headers: " + e.toString());
    }
  }

  @Override
  public Iterator<Map.Entry<String, String>> iterator() {
    throw new UnsupportedOperationException("This class should be used only with tracer#inject()");
  }
}
