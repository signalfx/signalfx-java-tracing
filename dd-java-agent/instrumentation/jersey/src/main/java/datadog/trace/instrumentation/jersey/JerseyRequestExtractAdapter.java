package datadog.trace.instrumentation.jersey;

import io.opentracing.propagation.TextMap;
import java.util.AbstractMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.ws.rs.core.MultivaluedMap;
import org.glassfish.jersey.server.ContainerRequest;

public class JerseyRequestExtractAdapter implements TextMap {
  private final MultivaluedMap<String, String> headers;

  JerseyRequestExtractAdapter(final ContainerRequest request) {
    headers = request.getHeaders();
  }

  @Override
  public Iterator<Map.Entry<String, String>> iterator() {
    return new MultivaluedMapIterator<>(headers.entrySet());
  }

  @Override
  public void put(final String key, final String value) {
    throw new UnsupportedOperationException("This class should be used only with Tracer.inject()!");
  }

  public static final class MultivaluedMapIterator<String>
      implements Iterator<Map.Entry<String, String>> {

    private final Iterator<Map.Entry<String, List<String>>> iterator;

    public MultivaluedMapIterator(final Set<Map.Entry<String, List<String>>> entrySet) {
      iterator = entrySet.iterator();
    }

    public boolean hasNext() {
      return iterator.hasNext();
    }

    public void remove() {
      throw new UnsupportedOperationException();
    }

    public Map.Entry<String, String> next() {
      Map.Entry<String, List<String>> entry = iterator.next();
      return new AbstractMap.SimpleImmutableEntry<>(entry.getKey(), entry.getValue().get(0));
    }
  }
}
