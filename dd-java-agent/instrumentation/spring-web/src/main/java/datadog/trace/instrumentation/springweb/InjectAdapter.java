package datadog.trace.instrumentation.springweb;

import io.opentracing.propagation.TextMap;
import java.util.Iterator;
import java.util.Map;
import org.springframework.http.HttpHeaders;

public class InjectAdapter implements TextMap {
  private HttpHeaders httpHeaders;

  public InjectAdapter(HttpHeaders httpHeaders) {
    this.httpHeaders = httpHeaders;
  }

  @Override
  public Iterator<Map.Entry<String, String>> iterator() {
    throw new UnsupportedOperationException("This class should be used only with tracer#inject()");
  }

  @Override
  public void put(String key, String value) {
    httpHeaders.set(key, value);
  }
}
