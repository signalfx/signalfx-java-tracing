package datadog.trace.instrumentation.jetty6;

import io.opentracing.propagation.TextMap;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;

public final class ServletHeaderAdapter implements TextMap {

  private final Map<String, List<String>> headers;

  public ServletHeaderAdapter(final HttpServletRequest request) {
    headers = new HashMap<>();

    Enumeration<String> headerNames = request.getHeaderNames();

    while (headerNames.hasMoreElements()) {
      String name = headerNames.nextElement().toString();
      List<String> values = Collections.list(request.getHeaders(name));

      headers.put(name, values);
    }
  }

  public Iterator<Map.Entry<String, String>> iterator() {
    return new MultivaluedMapFlatIterator(headers.entrySet().iterator());
  }

  @Override
  public void put(final String key, final String value) {
    throw new UnsupportedOperationException("This class should be used only with Tracer.inject()!");
  }

  private static final class MultivaluedMapFlatIterator
      implements Iterator<Map.Entry<String, String>> {

    private final Iterator<Map.Entry<String, List<String>>> setIterator;
    private Map.Entry<String, List<String>> currentEntry;
    private Iterator<String> listIterator;

    public MultivaluedMapFlatIterator(final Iterator<Map.Entry<String, List<String>>> iterator) {
      setIterator = iterator;
    }

    public boolean hasNext() {
      if (listIterator != null && listIterator.hasNext()) {
        return true;
      }

      return setIterator.hasNext();
    }

    public void remove() {
      throw new UnsupportedOperationException();
    }

    public Map.Entry<String, String> next() {
      if (currentEntry == null || (setIterator.hasNext() && !listIterator.hasNext())) {
        currentEntry = setIterator.next();
        listIterator = currentEntry.getValue().iterator();
      }

      return new AbstractMap.SimpleImmutableEntry<>(currentEntry.getKey(), listIterator.next());
    }
  }
}
