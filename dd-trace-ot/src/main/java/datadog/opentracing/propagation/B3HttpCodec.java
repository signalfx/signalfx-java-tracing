// Modified by SignalFx
package datadog.opentracing.propagation;

import datadog.opentracing.DDSpanContext;
import datadog.trace.api.Ids;
import datadog.trace.api.sampling.PrioritySampling;
import io.opentracing.SpanContext;
import io.opentracing.propagation.TextMapExtract;
import io.opentracing.propagation.TextMapInject;
import java.math.BigInteger;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * A codec designed for HTTP transport via headers using B3 headers
 *
 * <p>TODO: there is fair amount of code duplication between DatadogHttpCodec and this class,
 * especially in part where TagContext is handled. We may want to refactor that and avoid special
 * handling of TagContext in other places (i.e. CompoundExtractor).
 */
@Slf4j
class B3HttpCodec {

  static final BigInteger UINT128_MAX = new BigInteger("2").pow(128).subtract(BigInteger.ONE);

  private static final String OT_BAGGAGE_PREFIX = "ot-baggage-";
  private static final String TRACE_ID_KEY = "x-b3-traceid";
  private static final String SPAN_ID_KEY = "x-b3-spanid";
  private static final String PARENT_SPAN_ID_KEY = "x-b3-parentspanid";
  private static final String SAMPLING_PRIORITY_KEY = "x-b3-sampled";
  private static final String FLAGS_KEY = "x-b3-flags";
  private static final int HEX_RADIX = 16;

  private B3HttpCodec() {
    // This class should not be created. This also makes code coverage checks happy.
  }

  public static class Injector implements HttpCodec.Injector {

    @Override
    public void inject(final DDSpanContext context, final TextMapInject carrier) {
      try {
        carrier.put(TRACE_ID_KEY, String.valueOf(Ids.idToHexChars(context.getTraceId())));
        carrier.put(SPAN_ID_KEY, String.valueOf(Ids.idToHexChars(context.getSpanId())));

        if (context.getParentId() != null) {
          carrier.put(PARENT_SPAN_ID_KEY, String.valueOf(Ids.idToHexChars(context.getParentId())));
        }

        if (context.lockSamplingPriority()) {
          int ps = context.getSamplingPriority();
          switch (ps) {
            case PrioritySampling.USER_KEEP:
              // Set the debug flag if the user has manually marked the span to keep
              carrier.put(FLAGS_KEY, "1");
              // We don't need to set sampled in this case since it is implied
              break;
            case PrioritySampling.SAMPLER_KEEP:
              carrier.put(SAMPLING_PRIORITY_KEY, "1");
              break;
            case PrioritySampling.SAMPLER_DROP:
            case PrioritySampling.USER_DROP:
              carrier.put(SAMPLING_PRIORITY_KEY, "0");
          }
        }

        for (final Map.Entry<String, String> entry : context.baggageItems()) {
          carrier.put(OT_BAGGAGE_PREFIX + entry.getKey(), HttpCodec.encode(entry.getValue()));
        }

        log.debug("{} - B3 parent context injected", context.getTraceId());
      } catch (final NumberFormatException e) {
        log.debug(
            "Cannot parse context id(s): {} {}", context.getTraceId(), context.getSpanId(), e);
      }
    }
  }

  public static class Extractor implements HttpCodec.Extractor {

    private final Map<String, String> taggedHeaders;

    public Extractor(final Map<String, String> taggedHeaders) {
      this.taggedHeaders = new HashMap<>();
      for (final Map.Entry<String, String> mapping : taggedHeaders.entrySet()) {
        this.taggedHeaders.put(mapping.getKey().trim().toLowerCase(), mapping.getValue());
      }
    }

    public Extractor() {
      this.taggedHeaders = new HashMap<>();
    }

    @Override
    public SpanContext extract(final TextMapExtract carrier) {
      try {
        Map<String, String> tags = Collections.emptyMap();
        BigInteger traceId = BigInteger.ZERO;
        BigInteger spanId = BigInteger.ZERO;
        int samplingPriority = PrioritySampling.UNSET;

        for (final Map.Entry<String, String> entry : carrier) {
          final String key = entry.getKey().toLowerCase();
          final String value = entry.getValue();

          if (value == null) {
            continue;
          }

          if (TRACE_ID_KEY.equalsIgnoreCase(key)) {
            final int length = value.length();
            if (length > 32) {
              log.debug("Header {} exceeded max length of 32: {}", TRACE_ID_KEY, value);
              continue;
            }
            traceId = validateUInt128BitsID(value, HEX_RADIX);
          } else if (SPAN_ID_KEY.equalsIgnoreCase(key)) {
            spanId = validateUInt128BitsID(value, HEX_RADIX);
          } else if (SAMPLING_PRIORITY_KEY.equalsIgnoreCase(key)) {
            samplingPriority = convertSamplingPriority(value);
          }

          if (taggedHeaders.containsKey(key)) {
            if (tags.isEmpty()) {
              tags = new HashMap<>();
            }
            tags.put(taggedHeaders.get(key), HttpCodec.decode(value));
          }
        }

        if (!BigInteger.ZERO.equals(traceId)) {
          final ExtractedContext context =
              new ExtractedContext(
                  traceId,
                  spanId,
                  samplingPriority,
                  null,
                  Collections.<String, String>emptyMap(),
                  tags);
          context.lockSamplingPriority();

          log.debug("{} - Parent context extracted", context.getTraceId());
          return context;
        } else if (!tags.isEmpty()) {
          log.debug("Tags context extracted");
          return new TagContext(null, tags);
        }
      } catch (final RuntimeException e) {
        log.debug("Exception when extracting context", e);
      }

      return null;
    }

    static BigInteger validateUInt128BitsID(final String value, final int radix)
        throws IllegalArgumentException {
      final BigInteger parsedValue = new BigInteger(value, radix);
      if (parsedValue.compareTo(BigInteger.ZERO) == -1 || parsedValue.compareTo(UINT128_MAX) == 1) {
        throw new IllegalArgumentException(
            "ID out of range, must be between 0 and 2^128-1, got: " + value);
      }
      return parsedValue;
    }

    private int convertSamplingPriority(final String samplingPriority) {
      return Integer.parseInt(samplingPriority) == 1
          ? PrioritySampling.SAMPLER_KEEP
          : PrioritySampling.SAMPLER_DROP;
    }
  }
}
