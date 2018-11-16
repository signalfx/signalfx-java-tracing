package datadog.opentracing.mock.composite;

import datadog.opentracing.mock.*;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.mock.MockSpan.MockContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class CompositeSpan implements Span {
  protected Span[] childSpans;

  public CompositeSpan(Span[] spans) {
    childSpans = spans;
  }

  public class CompositeSpanContext implements SpanContext {
    protected SpanContext[] childSpanContexts;

    public CompositeSpanContext(SpanContext[] spanContexts) {
      childSpanContexts = spanContexts;
    }

    @Override
    public Iterable<Entry<String, String>> baggageItems() {
      return null;
    }

    public long traceId() {
      return ((MockContext) CompositeSpan.this.childSpans[0].context()).traceId();
    }

    public long spanId() {
      return ((MockContext) CompositeSpan.this.childSpans[0].context()).spanId();
    }
  }

  public static CompositeSpanContext toCompositeContext(SpanContext[] spanContexts) {
    CompositeSpan cs = new CompositeSpan(new Span[0]);
    return cs.new CompositeSpanContext(spanContexts);
  }

  @Override
  public SpanContext context() {
    List<SpanContext> spanContexts = new ArrayList<>();
    for (Span span : childSpans) {
      spanContexts.add(span.context());
    }
    return new CompositeSpanContext(spanContexts.toArray(new SpanContext[0]));
  }

  @Override
  public Span setTag(String s, String s1) {
    for (Span span : childSpans) {
      span.setTag(s, s1);
    }
    return this;
  }

  @Override
  public Span setTag(String s, boolean b) {
    for (Span span : childSpans) {
      span.setTag(s, b);
    }
    return this;
  }

  @Override
  public Span setTag(String s, Number number) {
    for (Span span : childSpans) {
      span.setTag(s, number);
    }
    return this;
  }

  @Override
  public Span log(Map<String, ?> map) {
    for (Span span : childSpans) {
      span.log(map);
    }
    return this;
  }

  @Override
  public Span log(long l, Map<String, ?> map) {
    for (Span span : childSpans) {
      span.log(map);
    }
    return this;
  }

  @Override
  public Span log(String s) {
    for (Span span : childSpans) {
      span.log(s);
    }
    return this;
  }

  @Override
  public Span log(long l, String s) {
    for (Span span : childSpans) {
      span.log(l, s);
    }
    return this;
  }

  @Override
  public Span setBaggageItem(String s, String s1) {
    for (Span span : childSpans) {
      span.setBaggageItem(s, s1);
    }
    return this;
  }

  @Override
  public String getBaggageItem(String s) {
    return childSpans[0].getBaggageItem(s);
  }

  @Override
  public Span setOperationName(String s) {
    for (Span span : childSpans) {
      span.setOperationName(s);
    }
    return this;
  }

  @Override
  public void finish() {
    for (Span span : childSpans) {
      span.finish();
    }
  }

  @Override
  public void finish(long l) {
    for (Span span : childSpans) {
      span.finish(l);
    }
  }

  @Override
  public String toString() {
    String rep = "CompositeSpan: ";
    for (Span span : childSpans) {
      rep += span.toString() + "...";
    }
    return rep;
  }
}
