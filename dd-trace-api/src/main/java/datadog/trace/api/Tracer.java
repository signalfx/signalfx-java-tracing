// Modified by SignalFx
package datadog.trace.api;

import datadog.trace.api.interceptor.TraceInterceptor;

/** A class with Datadog tracer features. */
public interface Tracer {

  /** Get the trace id of the active trace. Returns 0 if there is no active trace. */
  long getTraceId();

  /**
   * Get the span id of the active span of the active trace. Returns 0 if there is no active trace.
   */
  long getSpanId();

  /**
   * Add a new interceptor to the tracer. Interceptors with duplicate priority to existing ones are
   * ignored.
   *
   * @param traceInterceptor
   * @return false if an interceptor with same priority exists.
   */
  boolean addTraceInterceptor(TraceInterceptor traceInterceptor);
}
