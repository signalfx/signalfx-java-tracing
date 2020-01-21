// Modified by SignalFx
package datadog.trace.agent.decorator;

import datadog.trace.instrumentation.api.AgentSpan;
import io.opentracing.tag.Tags;

public abstract class ServerDecorator extends BaseDecorator {

  @Override
  public AgentSpan afterStart(final AgentSpan span) {
    assert span != null;
    span.setTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER);
    return super.afterStart(span);
  }

  public AgentSpan afterStart(final AgentSpan span, final boolean setKind) {
    assert span != null;
    if (setKind) {
      return this.afterStart(span);
    }
    return super.afterStart(span);
  }
}
