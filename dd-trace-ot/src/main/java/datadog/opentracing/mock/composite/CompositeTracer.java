package datadog.opentracing.mock.composite;

import io.opentracing.Scope;
import io.opentracing.ScopeManager;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ListIterator;

public class CompositeTracer implements Tracer, datadog.trace.api.Tracer {
  protected Tracer[] childTracers;

  // first tracer MUST be a TestTracer
  public CompositeTracer(Tracer[] tracers) {
    childTracers = tracers;
  }

  @Override
  public long getTraceId() {
    return ((datadog.trace.api.Tracer) childTracers[0]).getTraceId();
  }

  @Override
  public long getSpanId() {
    return ((datadog.trace.api.Tracer) childTracers[0]).getSpanId();
  }

  @Override
  public String toString() {
    String rep = "CompositeTracer: ";
    for (Tracer tracer : childTracers) {
      rep += tracer.toString() + "...";
    }
    return rep;
  }

  @Override
  public ScopeManager scopeManager() {
    return new CompositeScopeManager(childTracers);
  }

  @Override
  public Span activeSpan() {
    Scope scope = scopeManager().active();
    if (scope == null) {
      return null;
    }
    return scope.span();
  }

  @Override
  public SpanBuilder buildSpan(String s) {
    return new CompositeSpanBuilder(childTracers, s);
  }

  @Override
  // spanContext should always be a CompositeSpanContext
  public <C> void inject(SpanContext spanContext, Format<C> format, C c) {
    List<SpanContext> childSpanContexts =
        Arrays.asList(((CompositeSpan.CompositeSpanContext) spanContext).childSpanContexts);
    ListIterator iter = childSpanContexts.listIterator();
    while (iter.hasNext()) {
      childTracers[iter.nextIndex()].inject((SpanContext) iter.next(), format, c);
    }
  }

  @Override
  public <C> SpanContext extract(Format<C> format, C c) {
    List<SpanContext> spanContexts = new ArrayList<>();
    for (Tracer tracer : childTracers) {
      spanContexts.add(tracer.extract(format, c));
    }
    return CompositeSpan.toCompositeContext(spanContexts.toArray(new SpanContext[0]));
  }

  public class CompositeSpanBuilder implements SpanBuilder {
    protected SpanBuilder[] childSpanBuilders;

    public CompositeSpanBuilder(Tracer[] tracers, String s) {
      List<SpanBuilder> spanBuilders = new ArrayList<>();
      for (Tracer tracer : tracers) {
        SpanBuilder sb = tracer.buildSpan(s);
        spanBuilders.add(sb);
      }
      childSpanBuilders = spanBuilders.toArray(new SpanBuilder[0]);
    }

    @Override
    // spanContext must be CompositeSpanContext
    public SpanBuilder asChildOf(SpanContext spanContext) {
      if (spanContext == null) {
        return this;
      }
      List<SpanContext> childSpanContexts =
          Arrays.asList(((CompositeSpan.CompositeSpanContext) spanContext).childSpanContexts);
      ListIterator iter = childSpanContexts.listIterator();
      while (iter.hasNext()) {
        childSpanBuilders[iter.nextIndex()].asChildOf((SpanContext) iter.next());
      }
      return this;
    }

    @Override
    // span must be CompositeSpan
    public SpanBuilder asChildOf(Span span) {
      if (span == null) {
        return this;
      }
      List<Span> childSpans = Arrays.asList(((CompositeSpan) span).childSpans);
      ListIterator iter = childSpans.listIterator();
      while (iter.hasNext()) {
        childSpanBuilders[iter.nextIndex()].asChildOf((Span) iter.next());
      }
      return this;
    }

    @Override
    public SpanBuilder addReference(String s, SpanContext spanContext) {
      for (SpanBuilder sb : childSpanBuilders) {
        sb.addReference(s, spanContext);
      }
      return this;
    }

    @Override
    public SpanBuilder ignoreActiveSpan() {
      for (SpanBuilder sb : childSpanBuilders) {
        sb.ignoreActiveSpan();
      }
      return this;
    }

    @Override
    public SpanBuilder withTag(String s, String s1) {
      for (SpanBuilder sb : childSpanBuilders) {
        sb.withTag(s, s1);
      }
      return this;
    }

    @Override
    public SpanBuilder withTag(String s, boolean b) {
      for (SpanBuilder sb : childSpanBuilders) {
        sb.withTag(s, b);
      }
      return this;
    }

    @Override
    public SpanBuilder withTag(String s, Number number) {
      for (SpanBuilder sb : childSpanBuilders) {
        sb.withTag(s, number);
      }
      return this;
    }

    @Override
    public SpanBuilder withStartTimestamp(long l) {
      for (SpanBuilder sb : childSpanBuilders) {
        sb.withStartTimestamp(l);
      }
      return this;
    }

    @Override
    public Scope startActive(boolean b) {
      return CompositeTracer.this.scopeManager().activate(this.start(), b);
    }

    @Override
    public Span startManual() {
      return start();
    }

    @Override
    public Span start() {
      List<Span> spans = new ArrayList<>();
      for (SpanBuilder spanBuilder : childSpanBuilders) {
        spans.add(spanBuilder.start());
      }
      return new CompositeSpan(spans.toArray(new Span[0]));
    }
  }
}
