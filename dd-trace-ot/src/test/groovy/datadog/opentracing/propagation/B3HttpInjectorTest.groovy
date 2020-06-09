// Modified by SignalFx
package datadog.opentracing.propagation

import datadog.opentracing.DDSpanContext
import datadog.opentracing.DDTracer
import datadog.opentracing.PendingTrace
import datadog.trace.api.sampling.PrioritySampling
import datadog.trace.common.writer.ListWriter
import datadog.trace.util.test.DDSpecification
import io.opentracing.propagation.TextMapInjectAdapter

import static datadog.opentracing.propagation.B3HttpCodec.SAMPLING_PRIORITY_KEY
import static datadog.opentracing.propagation.B3HttpCodec.PARENT_SPAN_ID_KEY
import static datadog.opentracing.propagation.B3HttpCodec.OT_BAGGAGE_PREFIX
import static datadog.opentracing.propagation.B3HttpCodec.TRACE_ID_KEY
import static datadog.opentracing.propagation.B3HttpCodec.SPAN_ID_KEY
import static datadog.opentracing.propagation.B3HttpCodec.FLAGS_KEY
import static datadog.opentracing.propagation.B3HttpCodec.UINT128_MAX

class B3HttpInjectorTest extends DDSpecification {

  HttpCodec.Injector injector = new B3HttpCodec.Injector()
  static BigInteger uint64Max = 2G.pow(64).subtract(1G)

  def "inject http headers #samplingPriority : #expectedSamplingPriority"() {
    setup:
    def writer = new ListWriter()
    def tracer = DDTracer.builder().writer(writer).build()
    final DDSpanContext mockedContext =
      new DDSpanContext(
        traceId,
        spanId,
        0G,
        "fakeService",
        "fakeOperation",
        "fakeResource",
        samplingPriority,
        "fakeOrigin",
        new HashMap<String, String>() {
          {
            put("k1", "v1")
            put("k2", "v2")
          }
        },
        false,
        "fakeType",
        null,
        new PendingTrace(tracer, 1G),
        tracer,
        [:])

    final Map<String, String> carrier = Mock()

    when:
    injector.inject(mockedContext, new TextMapInjectAdapter(carrier))

    then:
    1 * carrier.put(TRACE_ID_KEY, String.format("%016x", traceId))
    1 * carrier.put(SPAN_ID_KEY, String.format("%016x", spanId))
    1 * carrier.put(PARENT_SPAN_ID_KEY, '0' * 16)
    1 * carrier.put(OT_BAGGAGE_PREFIX + "k1", "v1")
    1 * carrier.put(OT_BAGGAGE_PREFIX + "k2", "v2")
    if (samplingPriority == PrioritySampling.USER_KEEP) {
      1 * carrier.put(FLAGS_KEY, "$expectedSamplingPriority")
    } else if (expectedSamplingPriority != null) {
      1 * carrier.put(SAMPLING_PRIORITY_KEY, "$expectedSamplingPriority")
    }
    0 * _

    where:
    traceId              | spanId               | samplingPriority              | expectedSamplingPriority
    1G                   | 2G                   | PrioritySampling.UNSET        | null
    2G                   | 3G                   | PrioritySampling.SAMPLER_KEEP | 1
    4G                   | 5G                   | PrioritySampling.SAMPLER_DROP | 0
    5G                   | 6G                   | PrioritySampling.USER_KEEP    | 1
    6G                   | 7G                   | PrioritySampling.USER_DROP    | 0
    uint64Max            | uint64Max.minus(1)   | PrioritySampling.UNSET        | null
    uint64Max.minus(1)   | uint64Max            | PrioritySampling.SAMPLER_KEEP | 1
    UINT128_MAX          | UINT128_MAX.minus(1) | PrioritySampling.UNSET        | null
    UINT128_MAX.minus(1) | UINT128_MAX          | PrioritySampling.SAMPLER_KEEP | 1
  }
}
