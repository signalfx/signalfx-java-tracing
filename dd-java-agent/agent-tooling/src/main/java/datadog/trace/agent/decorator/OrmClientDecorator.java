// Modified by SignalFx
package datadog.trace.agent.decorator;

import datadog.trace.api.DDTags;
import io.opentracing.Span;

public abstract class OrmClientDecorator extends DatabaseClientDecorator {

  public abstract String entityName(final Object entity);

  public Span onOperation(final Span span, final Object entity) {

    assert span != null;
    if (entity != null) {
      final String name = entityName(entity);
      if (name != null) {
        span.setTag(DDTags.ENTITY_NAME, name);
      }
    }
    return span;
  }
}
