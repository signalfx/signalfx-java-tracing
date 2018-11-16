package datadog.opentracing.mock;

import datadog.opentracing.mock.composite.CompositeTracer;
import datadog.opentracing.scopemanager.ContextualScopeManager;
import datadog.trace.api.Tracer;
import io.opentracing.Span;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockSpan.MockContext;
import io.opentracing.mock.MockTracer;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMap;
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
    final Span activeSpan = TestTracer.this.activeSpan();
    if (activeSpan != null) {
      return ((MockContext) activeSpan.context()).traceId();
    }
    return 0;
  }

  @Override
  public long getSpanId() {
    final Span activeSpan = TestTracer.this.activeSpan();
    if (activeSpan != null) {
      return ((MockContext) activeSpan.context()).spanId();
    }
    return 0;
  }

  // Helpful to confirm that CompositeSpanContext propagation is functional
  // taken from opentracing-java/opentracing-mock/src/main/java/io/opentracing/mock/MockTracer.java
  public static Propagator CONFLICT_FREE_TEXT_MAP =
      new Propagator() {
        public static final String CF_SPAN_ID_KEY = "cf-spanid";
        public static final String CF_TRACE_ID_KEY = "cf-traceid";
        public static final String CF_BAGGAGE_KEY_PREFIX = "cf-baggage-";

        @Override
        public <C> void inject(MockSpan.MockContext ctx, Format<C> format, C carrier) {
          if (carrier instanceof TextMap) {
            TextMap textMap = (TextMap) carrier;
            for (Map.Entry<String, String> entry : ctx.baggageItems()) {
              textMap.put(CF_BAGGAGE_KEY_PREFIX + entry.getKey(), entry.getValue());
            }
            textMap.put(CF_SPAN_ID_KEY, String.valueOf(ctx.spanId()));
            textMap.put(CF_TRACE_ID_KEY, String.valueOf(ctx.traceId()));
          } else {
            throw new IllegalArgumentException("Unknown carrier");
          }
        }

        @Override
        public <C> MockSpan.MockContext extract(Format<C> format, C carrier) {
          Long traceId = null;
          Long spanId = null;
          Map<String, String> baggage = new HashMap<>();

          if (carrier instanceof TextMap) {
            TextMap textMap = (TextMap) carrier;
            for (Map.Entry<String, String> entry : textMap) {
              if (CF_TRACE_ID_KEY.equals(entry.getKey())) {
                traceId = Long.valueOf(entry.getValue());
              } else if (CF_SPAN_ID_KEY.equals(entry.getKey())) {
                spanId = Long.valueOf(entry.getValue());
              } else if (entry.getKey().startsWith(CF_BAGGAGE_KEY_PREFIX)) {
                String key = entry.getKey().substring((CF_BAGGAGE_KEY_PREFIX.length()));
                baggage.put(key, entry.getValue());
              }
            }
          } else {
            throw new IllegalArgumentException("Unknown carrier");
          }

          if (traceId != null && spanId != null) {
            return new MockSpan.MockContext(traceId, spanId, baggage);
          }

          return null;
        }
      };

  public static io.opentracing.Tracer configureTestTracer(TestTracer testTracer) {
    if (System.getProperty("mock.tracer.composite") != null) {
      System.out.println(
          "AgentTestRunner.static initializer mock.tracer.composite: "
              + System.getProperty("mock.tracer.composite"));
      io.jaegertracing.internal.propagation.B3TextMapCodec b3Codec =
          new io.jaegertracing.internal.propagation.B3TextMapCodec.Builder().build();
      io.opentracing.Tracer jaegerTracer =
          io.jaegertracing.Configuration.fromEnv()
              .getTracerBuilder()
              .withScopeManager(new ContextualScopeManager())
              .registerInjector(Format.Builtin.HTTP_HEADERS, b3Codec)
              .registerExtractor(Format.Builtin.HTTP_HEADERS, b3Codec)
              .build();
      System.out.println("AgentTestRunner.static initializer: Building CompositeTracer.");
      return new CompositeTracer(new io.opentracing.Tracer[] {testTracer, jaegerTracer});
    }
    return testTracer;
  }
}
