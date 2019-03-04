// Modified by SignalFx
package datadog.opentracing.decorators;

import datadog.opentracing.DDSpanContext;
import datadog.trace.api.DDTags;

public class ServiceNameDecorator extends AbstractDecorator {

  public ServiceNameDecorator() {
    super();
    this.setMatchingTag(DDTags.SERVICE_NAME);
  }

  @Override
  public boolean shouldSetTag(final DDSpanContext context, final String tag, final Object value) {
    // The DD service.name tag has a lot of overlap with component and can generally just be
    // ignored.  For overridding
    // the service, use the "service" tag, handled by the ServiceDecorator.
    // context.setServiceName(String.valueOf(value));
    return false;
  }
}
