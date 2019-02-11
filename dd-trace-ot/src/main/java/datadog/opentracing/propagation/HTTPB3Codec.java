package datadog.opentracing.propagation;

import com.google.common.base.Strings;
import datadog.opentracing.DDSpanContext;
import datadog.trace.api.sampling.PrioritySampling;
import datadog.trace.common.util.Ids;
import io.opentracing.SpanContext;
import io.opentracing.propagation.TextMap;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * A codec designed for B3 HTTP transport via headers
 *
 * <p>Spec is at https://github.com/openzipkin/b3-propagation
 */
@Slf4j
public class HTTPB3Codec implements Codec<TextMap> {

  // uint 64 bits max value, 2^64 - 1
  static final BigInteger BIG_INTEGER_UINT64_MAX =
      new BigInteger("2").pow(64).subtract(BigInteger.ONE);

  private static final String OT_BAGGAGE_PREFIX = "ot-baggage-";
  private static final String TRACE_ID_KEY = "x-b3-traceid";
  private static final String SPAN_ID_KEY = "x-b3-spanid";
  private static final String PARENT_SPAN_ID_KEY = "x-b3-parentspanid";
  private static final String SAMPLED_KEY = "x-b3-sampled";
  private static final String FLAGS_KEY = "x-b3-flags";

  public HTTPB3Codec() {}

  @Override
  public void inject(final DDSpanContext context, final TextMap carrier) {
    carrier.put(TRACE_ID_KEY, Ids.idToHex(context.getTraceId()));
    carrier.put(SPAN_ID_KEY, Ids.idToHex(context.getSpanId()));
    if (!Strings.isNullOrEmpty(context.getParentId())) {
      carrier.put(PARENT_SPAN_ID_KEY, Ids.idToHex(context.getParentId()));
    }

    int ps = context.getSamplingPriority();
    switch (ps) {
      case PrioritySampling.USER_KEEP:
        // Set the debug flag if the user has manually marked the span to keep
        carrier.put(FLAGS_KEY, "1");
        // We don't need to set sampled in this case since it is implied
        break;
      case PrioritySampling.SAMPLER_KEEP:
        carrier.put(SAMPLED_KEY, "1");
        break;
      case PrioritySampling.SAMPLER_DROP:
      case PrioritySampling.USER_DROP:
        carrier.put(SAMPLED_KEY, "0");
    }

    for (final Map.Entry<String, String> entry : context.baggageItems()) {
      carrier.put(OT_BAGGAGE_PREFIX + entry.getKey(), encode(entry.getValue()));
    }
    log.debug("{} - Parent context injected", context.getTraceId());
  }

  @Override
  public SpanContext extract(final TextMap carrier) {
    Map<String, String> baggage = Collections.emptyMap();
    Map<String, String> tags = Collections.emptyMap();
    String traceId = "0";
    String spanId = "0";
    int samplingPriority = PrioritySampling.UNSET;

    for (final Map.Entry<String, String> entry : carrier) {
      final String key = entry.getKey().toLowerCase();
      final String val = entry.getValue();

      if (val == null) {
        continue;
      }

      // No need to decode parent span id since we don't use it for anything.
      if (TRACE_ID_KEY.equalsIgnoreCase(key)) {
        traceId = validateUInt64BitsID(Ids.hexToId(val));
      } else if (SPAN_ID_KEY.equalsIgnoreCase(key)) {
        spanId = validateUInt64BitsID(Ids.hexToId(val));
      } else if (key.startsWith(OT_BAGGAGE_PREFIX)) {
        if (baggage.isEmpty()) {
          baggage = new HashMap<>();
        }
        baggage.put(key.replace(OT_BAGGAGE_PREFIX, ""), decode(val));
      } else if (SAMPLED_KEY.equalsIgnoreCase(key)) {
        if ("1".equals(val)) {
          samplingPriority = PrioritySampling.SAMPLER_KEEP;
        } else if ("0".equals(val)) {
          samplingPriority = PrioritySampling.SAMPLER_DROP;
        } else {
          log.debug("Unknown B3 sampled header value: {}", val);
        }
      } else if (FLAGS_KEY.equalsIgnoreCase(key)) {
        if ("1".equals(val)) {
          samplingPriority = PrioritySampling.USER_KEEP;
        }
      }
    }

    SpanContext context = null;
    if (!"0".equals(traceId)) {
      final ExtractedContext ctx =
          new ExtractedContext(traceId, spanId, samplingPriority, baggage, tags);
      ctx.lockSamplingPriority();

      log.debug("{} - Parent context extracted", ctx.getTraceId());
      context = ctx;
    }

    return context;
  }

  private String encode(final String value) {
    String encoded = value;
    try {
      encoded = URLEncoder.encode(value, "UTF-8");
    } catch (final UnsupportedEncodingException e) {
      log.info("Failed to encode value - {}", value);
    }
    return encoded;
  }

  private String decode(final String value) {
    String decoded = value;
    try {
      decoded = URLDecoder.decode(value, "UTF-8");
    } catch (final UnsupportedEncodingException e) {
      log.info("Failed to decode value - {}", value);
    }
    return decoded;
  }

  /**
   * Helper method to validate an ID String to verify that it is an unsigned 64 bits number and is
   * within range.
   *
   * @param val the String that contains the ID
   * @return the ID in String format if it passes validations
   * @throws IllegalArgumentException if val is not a number or if the number is out of range
   */
  private String validateUInt64BitsID(final String val) throws IllegalArgumentException {
    try {
      final BigInteger validate = new BigInteger(val);
      if (validate.compareTo(BigInteger.ZERO) == -1
          || validate.compareTo(BIG_INTEGER_UINT64_MAX) == 1) {
        throw new IllegalArgumentException(
            "ID out of range, must be between 0 and 2^64-1, got: " + val);
      }
      return val;
    } catch (final NumberFormatException nfe) {
      throw new IllegalArgumentException(
          "Expecting a number for trace ID or span ID, but got: " + val, nfe);
    }
  }
}
