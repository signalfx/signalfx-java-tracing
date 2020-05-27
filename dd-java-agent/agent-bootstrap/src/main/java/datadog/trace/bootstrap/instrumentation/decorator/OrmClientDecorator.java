// Modified by SignalFx
package datadog.trace.bootstrap.instrumentation.decorator;

import datadog.trace.api.DDTags;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;

public abstract class OrmClientDecorator extends DatabaseClientDecorator {

  public abstract String entityName(final Object entity);

  public AgentSpan onOperation(final AgentSpan span, final Object entity) {

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
