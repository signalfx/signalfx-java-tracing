package datadog.opentracing.propagation;

import datadog.opentracing.DDSpanContext;
import io.opentracing.propagation.TextMap;

public interface Injector {
  void inject(final DDSpanContext context, final TextMap carrier);
}
