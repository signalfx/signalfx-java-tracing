// Modified by SignalFx
package datadog.trace.api;

/**
 * Utility class to access the active trace and span ids.
 *
 * <p>Intended for use with MDC frameworks.
 */
public class CorrelationIdentifier {
  private static final String TRACE_ID_KEY = "signalfx.trace_id";
  private static final String SPAN_ID_KEY = "signalfx.span_id";
  private static final String SERVICE_NAME_KEY = "signalfx.service";
  private static final String ENVIRONMENT_NAME_KEY = "signalfx.environment";

  /** @return The trace-id key to use with datadog logs integration */
  public static String getTraceIdKey() {
    return TRACE_ID_KEY;
  }

  /** @return The span-id key to use with datadog logs integration */
  public static String getSpanIdKey() {
    return SPAN_ID_KEY;
  }

  public static String getServiceNameKey() { return SERVICE_NAME_KEY; }

  public static String getEnvironmentNameKey() { return ENVIRONMENT_NAME_KEY; }

  public static String getTraceId() {
    return Ids.idToHex(GlobalTracer.get().getTraceId());
  }

  public static String getSpanId() {
    return Ids.idToHex(GlobalTracer.get().getSpanId());
  }

  public static String getServiceName() { return GlobalTracer.get().getServiceName(); }

  public static String getEnvironmentName() { return GlobalTracer.get().getEnvironmentName(); }
}
