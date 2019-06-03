// Modified by SignalFx
package datadog.trace.api.interceptor;

import java.util.AbstractMap;
import java.util.List;
import java.util.Map;

public interface MutableSpan {

  /** @return Start time with nanosecond scale, but millisecond resolution. */
  long getStartTime();

  /** @return Duration with nanosecond scale. */
  long getDurationNano();

  String getOperationName();

  MutableSpan setOperationName(final String serviceName);

  String getServiceName();

  MutableSpan setServiceName(final String serviceName);

  String getResourceName();

  MutableSpan setResourceName(final String resourceName);

  Integer getSamplingPriority();

  MutableSpan setSamplingPriority(final int newPriority);

  String getSpanType();

  MutableSpan setSpanType(final String type);

  Map<String, Object> getTags();

  MutableSpan setTag(final String tag, final String value);

  MutableSpan setTag(final String tag, final boolean value);

  MutableSpan setTag(final String tag, final Number value);

  List<AbstractMap.SimpleEntry<Long, Map<String, ?>>> getLogs();

  MutableSpan log(final java.util.Map<java.lang.String, ?> map);

  MutableSpan log(final long l, final java.util.Map<java.lang.String, ?> map);

  MutableSpan log(final java.lang.String s);

  MutableSpan log(final long l, final java.lang.String s);

  Boolean isError();

  MutableSpan setError(boolean value);

  MutableSpan getRootSpan();
}
