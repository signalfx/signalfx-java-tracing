package datadog.opentracing.propagation

import datadog.opentracing.DDSpanContext
import datadog.opentracing.DDTracer
import datadog.opentracing.PendingTrace
import datadog.trace.api.sampling.PrioritySampling
import datadog.trace.common.writer.ListWriter
import io.opentracing.propagation.TextMapExtractAdapter
import io.opentracing.propagation.TextMapInjectAdapter
import spock.lang.Shared
import spock.lang.Specification

import static datadog.opentracing.propagation.DatadogHttpCodec.BIG_INTEGER_UINT64_MAX

class B3HttpCodecTest extends Specification {
  @Shared
  private static final String OT_BAGGAGE_PREFIX = "ot-baggage-"
  @Shared
  private static final String TRACE_ID_KEY = "x-b3-traceid"
  @Shared
  private static final String SPAN_ID_KEY = "x-b3-spanid"
  @Shared
  private static final String SAMPLED_KEY = "x-b3-sampled"

  B3HttpCodec codec = new B3HttpCodec()

  def "inject http headers"() {
    setup:
    def writer = new ListWriter()
    def tracer = new DDTracer(writer)
    final DDSpanContext mockedContext =
      new DDSpanContext(
        "1",
        "2",
        "0",
        "fakeService",
        "fakeOperation",
        "fakeResource",
        samplingPriority,
        new HashMap<String, String>() {
          {
            put("k1", "v1")
            put("k2", "v2")
          }
        },
        false,
        "fakeType",
        null,
        new PendingTrace(tracer, "1", [:]),
        tracer)

    final Map<String, String> carrier = new HashMap<>()

    codec.inject(mockedContext, new TextMapInjectAdapter(carrier))

    expect:
    carrier.get(TRACE_ID_KEY) == "0000000000000001"
    carrier.get(SPAN_ID_KEY) == "0000000000000002"
    carrier.get(SAMPLED_KEY) == (samplingPriority == PrioritySampling.SAMPLER_KEEP ? "1" : null)
    carrier.get(OT_BAGGAGE_PREFIX + "k1") == "v1"
    carrier.get(OT_BAGGAGE_PREFIX + "k2") == "v2"

    where:
    samplingPriority              | _
    PrioritySampling.UNSET        | _
    PrioritySampling.SAMPLER_KEEP | _
  }

  def "inject http headers with larger than Java long IDs"() {
    String largeTraceId = "9523372036854775807"
    String largeSpanId = "15815582334751494918"
    String largeParentId = "15815582334751494914"
    setup:
    def writer = new ListWriter()
    def tracer = new DDTracer(writer)
    final DDSpanContext mockedContext =
      new DDSpanContext(
        largeTraceId,
        largeSpanId,
        largeParentId,
        "fakeService",
        "fakeOperation",
        "fakeResource",
        samplingPriority,
        new HashMap<String, String>() {
          {
            put("k1", "v1")
            put("k2", "v2")
          }
        },
        false,
        "fakeType",
        null,
        new PendingTrace(tracer, largeTraceId, [:]),
        tracer)

    final Map<String, String> carrier = new HashMap<>()

    codec.inject(mockedContext, new TextMapInjectAdapter(carrier))

    expect:
    carrier.get(TRACE_ID_KEY) == "8429d069189dffff"
    carrier.get(SPAN_ID_KEY) == "db7c3c56c2ce5306"
    carrier.get(SAMPLED_KEY) == (samplingPriority == PrioritySampling.UNSET ? null : String.valueOf(samplingPriority))
    carrier.get(OT_BAGGAGE_PREFIX + "k1") == "v1"
    carrier.get(OT_BAGGAGE_PREFIX + "k2") == "v2"

    where:
    samplingPriority              | _
    PrioritySampling.UNSET        | _
    PrioritySampling.SAMPLER_KEEP | _
  }

  def "inject http headers with uint 64 max IDs"() {
    String largeTraceId = "18446744073709551615"
    String largeSpanId = "18446744073709551614"
    String largeParentId = "18446744073709551613"
    setup:
    def writer = new ListWriter()
    def tracer = new DDTracer(writer)
    final DDSpanContext mockedContext =
      new DDSpanContext(
        largeTraceId,
        largeSpanId,
        largeParentId,
        "fakeService",
        "fakeOperation",
        "fakeResource",
        samplingPriority,
        new HashMap<String, String>() {
          {
            put("k1", "v1")
            put("k2", "v2")
          }
        },
        false,
        "fakeType",
        null,
        new PendingTrace(tracer, largeTraceId, [:]),
        tracer)

    final Map<String, String> carrier = new HashMap<>()

    codec.inject(mockedContext, new TextMapInjectAdapter(carrier))

    expect:
    carrier.get(TRACE_ID_KEY) == "ffffffffffffffff"
    carrier.get(SPAN_ID_KEY) == "fffffffffffffffe"
    carrier.get(SAMPLED_KEY) == (samplingPriority == PrioritySampling.UNSET ? null : String.valueOf(samplingPriority))
    carrier.get(OT_BAGGAGE_PREFIX + "k1") == "v1"
    carrier.get(OT_BAGGAGE_PREFIX + "k2") == "v2"

    where:
    samplingPriority              | _
    PrioritySampling.UNSET        | _
    PrioritySampling.SAMPLER_KEEP | _
  }

  def "inject http headers with bigger than uint 64 max IDs"() {
    String largeTraceId = "18446744073709551616"
    String largeSpanId = "18446744073709551615"
    String largeParentId = "18446744073709551613"
    setup:
    def writer = new ListWriter()
    def tracer = new DDTracer(writer)
    final DDSpanContext mockedContext =
      new DDSpanContext(
        largeTraceId,
        largeSpanId,
        largeParentId,
        "fakeService",
        "fakeOperation",
        "fakeResource",
        samplingPriority,
        new HashMap<String, String>() {
          {
            put("k1", "v1")
            put("k2", "v2")
          }
        },
        false,
        "fakeType",
        null,
        new PendingTrace(tracer, largeTraceId, [:]),
        tracer)

    final Map<String, String> carrier = new HashMap<>()

    codec.inject(mockedContext, new TextMapInjectAdapter(carrier))

    expect:
    carrier.get(TRACE_ID_KEY) == "00000000000000010000000000000000"
    carrier.get(SPAN_ID_KEY) == "ffffffffffffffff"
    carrier.get(SAMPLED_KEY) == (samplingPriority == PrioritySampling.UNSET ? null : String.valueOf(samplingPriority))
    carrier.get(OT_BAGGAGE_PREFIX + "k1") == "v1"
    carrier.get(OT_BAGGAGE_PREFIX + "k2") == "v2"

    where:
    samplingPriority              | _
    PrioritySampling.UNSET        | _
    PrioritySampling.SAMPLER_KEEP | _
  }

  def "extract http headers"() {
    setup:
    final Map<String, String> actual = [
      (TRACE_ID_KEY.toUpperCase())            : "1",
      (SPAN_ID_KEY.toUpperCase())             : "2",
      (OT_BAGGAGE_PREFIX.toUpperCase() + "k1"): "v1",
      (OT_BAGGAGE_PREFIX.toUpperCase() + "k2"): "v2",
      SOME_HEADER                             : "my-interesting-info",
    ]

    if (samplingPriority != PrioritySampling.UNSET) {
      actual.put(SAMPLED_KEY, "1")
    }

    final ExtractedContext context = codec.extract(new TextMapExtractAdapter(actual))

    expect:
    context.getTraceId() == "1"
    context.getSpanId() == "2"
    context.getBaggage().get("k1") == "v1"
    context.getBaggage().get("k2") == "v2"
    context.getSamplingPriority() == samplingPriority

    where:
    samplingPriority              | _
    PrioritySampling.UNSET        | _
    PrioritySampling.SAMPLER_KEEP | _
  }

  def "extract empty headers returns null"() {
    expect:
    codec.extract(new TextMapExtractAdapter(["ignored-header": "ignored-value"])) == null
  }

  def "extract http headers with larger than Java long IDs"() {
    setup:
    String largeTraceId = "8429d069189dffff"
    String largeSpanId = "db7c3c56c2ce5306"
    final Map<String, String> actual = [
      (TRACE_ID_KEY.toUpperCase())            : largeTraceId,
      (SPAN_ID_KEY.toUpperCase())             : largeSpanId,
      (OT_BAGGAGE_PREFIX.toUpperCase() + "k1"): "v1",
      (OT_BAGGAGE_PREFIX.toUpperCase() + "k2"): "v2",
      SOME_HEADER                             : "my-interesting-info",
    ]

    if (samplingPriority != PrioritySampling.UNSET) {
      actual.put(SAMPLED_KEY, "1")
    }

    final ExtractedContext context = codec.extract(new TextMapExtractAdapter(actual))

    expect:
    context.getTraceId() == "9523372036854775807"
    context.getSpanId() == "15815582334751494918"
    context.getBaggage().get("k1") == "v1"
    context.getBaggage().get("k2") == "v2"
    context.getSamplingPriority() == samplingPriority

    where:
    samplingPriority              | _
    PrioritySampling.UNSET        | _
    PrioritySampling.SAMPLER_KEEP | _
  }

  def "extract http headers with uint 64 max IDs and bigger"() {
    setup:
    final Map<String, String> actual = [
      (TRACE_ID_KEY.toUpperCase())            : "ffffffffffffffff",
      (SPAN_ID_KEY.toUpperCase())             : "fffffffffffffffe",
      (OT_BAGGAGE_PREFIX.toUpperCase() + "k1"): "v1",
      (OT_BAGGAGE_PREFIX.toUpperCase() + "k2"): "v2",
      SOME_HEADER                             : "my-interesting-info",
    ]

    if (samplingPriority != PrioritySampling.UNSET) {
      actual.put(SAMPLED_KEY, "1")
    }

    final ExtractedContext context = codec.extract(new TextMapExtractAdapter(actual))

    expect:
    context.getTraceId() == BIG_INTEGER_UINT64_MAX.toString()
    context.getSpanId() == BIG_INTEGER_UINT64_MAX.subtract(BigInteger.ONE).toString()
    context.getBaggage().get("k1") == "v1"
    context.getBaggage().get("k2") == "v2"
    context.getSamplingPriority() == samplingPriority

    where:
    samplingPriority              | _
    PrioritySampling.UNSET        | _
    PrioritySampling.SAMPLER_KEEP | _
  }

  def "extract http headers with invalid non-numeric ID"() {
    setup:
    final Map<String, String> actual = [
      (TRACE_ID_KEY.toUpperCase())            : "traceID",
      (SPAN_ID_KEY.toUpperCase())             : "spanID",
      (OT_BAGGAGE_PREFIX.toUpperCase() + "k1"): "v1",
      (OT_BAGGAGE_PREFIX.toUpperCase() + "k2"): "v2",
      SOME_HEADER                             : "my-interesting-info",
    ]

    if (samplingPriority != PrioritySampling.UNSET) {
      actual.put(SAMPLED_KEY, "1")
    }

    when:
    codec.extract(new TextMapExtractAdapter(actual))

    then:
    thrown(NumberFormatException)

    where:
    samplingPriority              | _
    PrioritySampling.UNSET        | _
    PrioritySampling.SAMPLER_KEEP | _
  }

  def "extract http headers with out of range trace ID"() {
    setup:
    String outOfRangeTraceId = BIG_INTEGER_UINT64_MAX.add(BigInteger.ONE).toString()
    final Map<String, String> actual = [
      (TRACE_ID_KEY.toUpperCase())            : outOfRangeTraceId,
      (SPAN_ID_KEY.toUpperCase())             : "0",
      (OT_BAGGAGE_PREFIX.toUpperCase() + "k1"): "v1",
      (OT_BAGGAGE_PREFIX.toUpperCase() + "k2"): "v2",
      SOME_HEADER                             : "my-interesting-info",
    ]

    if (samplingPriority != PrioritySampling.UNSET) {
      actual.put(SAMPLED_KEY, String.valueOf(samplingPriority))
    }

    when:
    codec.extract(new TextMapExtractAdapter(actual))

    then:
    thrown(IllegalArgumentException)

    where:
    samplingPriority              | _
    PrioritySampling.UNSET        | _
    PrioritySampling.SAMPLER_KEEP | _
  }

  def "extract http headers with out of range span ID"() {
    setup:
    final Map<String, String> actual = [
      (TRACE_ID_KEY.toUpperCase())            : "0",
      (SPAN_ID_KEY.toUpperCase())             : "-1",
      (OT_BAGGAGE_PREFIX.toUpperCase() + "k1"): "v1",
      (OT_BAGGAGE_PREFIX.toUpperCase() + "k2"): "v2",
      SOME_HEADER                             : "my-interesting-info",
    ]

    if (samplingPriority != PrioritySampling.UNSET) {
      actual.put(SAMPLED_KEY, String.valueOf(samplingPriority))
    }

    when:
    codec.extract(new TextMapExtractAdapter(actual))

    then:
    thrown(IllegalArgumentException)

    where:
    samplingPriority              | _
    PrioritySampling.UNSET        | _
    PrioritySampling.SAMPLER_KEEP | _
  }
}
