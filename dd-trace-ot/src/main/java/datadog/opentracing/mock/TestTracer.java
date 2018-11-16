package datadog.opentracing.mock;

import datadog.opentracing.scopemanager.ContextualScopeManager;
import datadog.trace.api.Tracer;
import io.opentracing.Span;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class TestTracer extends MockTracer implements Tracer {
  protected ListWriter listWriter;
  protected final Map<Long, ArrayList<TestSpan>> traceMap = new HashMap<>();
  public String serviceName = "unnamed-java-app";

  public TestTracer() {
    super(new ContextualScopeManager());
    this.listWriter = new ListWriter();
  }

  public TestTracer(ListWriter listWriter) {
    super(new ContextualScopeManager());
    this.listWriter = listWriter;
  }

  @Override
  protected void onSpanFinished(MockSpan span) {
    trackAndWriteTrace(span);
  }

  private void trackAndWriteTrace(MockSpan span) {
    long traceId = span.context().traceId();
    if (!traceMap.containsKey(traceId)) {
      traceMap.put(traceId, new ArrayList<TestSpan>());
    }
    ArrayList<TestSpan> trace = traceMap.get(traceId);
    trace.add(0, new TestSpan(span));
    if (span.parentId() == 0) {
      listWriter.write(trace);
    }
  }

  @Override
  public long getTraceId() {
    final Span activeSpan = activeSpan();
    if (activeSpan != null) {
      return ((MockSpan) activeSpan).context().traceId();
    }
    return 0;
  }

  @Override
  public long getSpanId() {
    final Span activeSpan = activeSpan();
    if (activeSpan != null) {
      return ((MockSpan) activeSpan).context().spanId();
    }
    return 0;
  }
}
