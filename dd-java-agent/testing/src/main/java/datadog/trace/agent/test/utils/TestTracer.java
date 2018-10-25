package datadog.trace.agent.test.utils;

import datadog.trace.common.writer.ListWriter;
import datadog.trace.api.Tracer;
import datadog.trace.api.interceptor.TraceInterceptor;
import datadog.opentracing.scopemanager.ContextualScopeManager;
import io.opentracing.Span;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import java.util.ArrayList;

public class TestTracer extends MockTracer implements Tracer{
  protected ListWriter listWriter;
  protected final ArrayList<MockSpan> unfinishedSpans = new ArrayList<>();

  public TestTracer(ListWriter listWriter) {
    super(new ContextualScopeManager());
    this.listWriter = listWriter;
  }

  @Override
  protected void onSpanFinished(MockSpan mockSpan) {
    unfinishedSpans.add(0, mockSpan);
    if (mockSpan.parentId() == 0) {
      listWriter.write(unfinishedSpans);
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

  @Override
  public boolean addTraceInterceptor(TraceInterceptor traceInterceptor) {
    return false;
  }
}
