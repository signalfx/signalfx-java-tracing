package datadog.trace.agent.test.utils;

import io.opentracing.mock.MockSpan;
import io.opentracing.tag.AbstractTag;
import io.opentracing.tag.Tags;

public class TestSpan {

  public final MockSpan span;

  public TestSpan(MockSpan span) {
    this.span = span;
  }

  private String getTagValue(AbstractTag tag) {
    String key = tag.getKey();
    return (String) span.tags().get(key);
  }

  public String getKind() {
    return getTagValue(Tags.SPAN_KIND);
  }

  public String getComponent() {
    return getTagValue(Tags.COMPONENT);
  }

  public String getDBType() {
    return getTagValue(Tags.DB_TYPE);
  }

  public String getDBStatement() {
    return getTagValue(Tags.DB_STATEMENT);
  }
}
