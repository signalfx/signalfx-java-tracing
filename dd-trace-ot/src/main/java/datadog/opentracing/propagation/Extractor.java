package datadog.opentracing.propagation;

import io.opentracing.SpanContext;
import io.opentracing.propagation.TextMap;

public interface Extractor {
  SpanContext extract(final TextMap carrier);
}
