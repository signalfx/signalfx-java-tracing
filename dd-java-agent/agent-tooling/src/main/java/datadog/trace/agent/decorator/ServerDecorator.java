// Modified by SignalFx
package datadog.trace.agent.decorator;

import io.opentracing.Span;
import io.opentracing.tag.Tags;

public abstract class ServerDecorator extends BaseDecorator {

  @Override
  public Span afterStart(final Span span) {
    assert span != null;
    Tags.SPAN_KIND.set(span, Tags.SPAN_KIND_SERVER);
    return super.afterStart(span);
  }

  public Span afterStart(final Span span, final boolean setKind) {
    assert span != null;
    if (setKind) {
      return this.afterStart(span);
    }
    return super.afterStart(span);
  }
}
