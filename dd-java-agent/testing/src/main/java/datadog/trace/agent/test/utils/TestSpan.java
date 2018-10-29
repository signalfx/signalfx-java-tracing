package datadog.trace.agent.test.utils;

import io.opentracing.Span;
import io.opentracing.mock.MockSpan;
import io.opentracing.tag.AbstractTag;
import io.opentracing.tag.Tags;
import java.util.Map;

public class TestSpan implements Span {

  public final MockSpan span;

  public TestSpan(MockSpan span) {
    this.span = span;
  }

  public Map<String, Object> getTags() {
    return span.tags();
  }

  public Map<String, Object> tags() {
    return span.tags();
  }

  private String getTagValue(AbstractTag tag) {
    String key = tag.getKey();
    return (String) span.tags().get(key);
  }

  public long parentId() {
    return span.parentId();
  }

  public String getKind() {
    return getTagValue(Tags.SPAN_KIND);
  }

  public String getComponent() {
    return getTagValue(Tags.COMPONENT);
  }

  public String getOperationName() {
    return span.operationName();
  }

  public String getService() {
    return (String) span.tags().get("service");
  }

  public String getServiceName() {
    return getService();
  }

  public String getDBType() {
    return getTagValue(Tags.DB_TYPE);
  }

  public String getDBStatement() {
    return getTagValue(Tags.DB_STATEMENT);
  }

  @Override
  public String toString() {
    return span.toString() + " " + span.tags().toString() + "\n";
  }

  @Override
  public MockSpan.MockContext context() {
    return span.context();
  }

  @Override
  public Span setTag(String s, String s1) {
    return span.setTag(s, s1);
  }

  @Override
  public Span setTag(String s, boolean b) {
    return span.setTag(s, b);
  }

  @Override
  public Span setTag(String s, Number number) {
    return span.setTag(s, number);
  }

  @Override
  public Span log(Map<String, ?> map) {
    return span.log(map);
  }

  @Override
  public Span log(long l, Map<String, ?> map) {
    return span.log(l, map);
  }

  @Override
  public Span log(String s) {
    return span.log(s);
  }

  @Override
  public Span log(long l, String s) {
    return span.log(l, s);
  }

  @Override
  public Span setBaggageItem(String s, String s1) {
    return span.setBaggageItem(s, s1);
  }

  @Override
  public String getBaggageItem(String s) {
    return span.getBaggageItem(s);
  }

  @Override
  public Span setOperationName(String s) {
    return span.setOperationName(s);
  }

  @Override
  public void finish() {
    span.finish();
  }

  @Override
  public void finish(long l) {
    span.finish(l);
  }
}
