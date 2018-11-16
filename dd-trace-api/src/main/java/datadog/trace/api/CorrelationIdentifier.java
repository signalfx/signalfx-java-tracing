// Modified by SignalFx
package datadog.trace.api;

/**
 * Utility class to access the active trace and span ids.
 *
 * <p>Intended for use with MDC frameworks.
 */
public class CorrelationIdentifier {
  public static long getTraceId() {
    return GlobalTracer.get().getTraceId();
  }

  public static long getSpanId() {
    return GlobalTracer.get().getSpanId();
  }
}
