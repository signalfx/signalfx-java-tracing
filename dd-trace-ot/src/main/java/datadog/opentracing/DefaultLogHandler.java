// Modified by SignalFx
package datadog.opentracing;

import static io.opentracing.log.Fields.ERROR_KIND;
import static io.opentracing.log.Fields.ERROR_OBJECT;
import static io.opentracing.log.Fields.MESSAGE;
import static io.opentracing.log.Fields.STACK;

import datadog.trace.api.DDTags;
import datadog.trace.common.util.Clock;
import java.util.Collections;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/** The default implementation of the LogHandler. */
@Slf4j
public class DefaultLogHandler implements LogHandler {
  @Override
  public void log(Map<String, ?> fields, DDSpan span) {
    final long currentTime = Clock.currentMicroTime();
    if (!extractError(fields, span)) {
      span.context().log(currentTime, fields);
    }
  }

  @Override
  public void log(long timestampMicroseconds, Map<String, ?> fields, DDSpan span) {
    if (!extractError(fields, span)) {
      span.context().log(timestampMicroseconds, fields);
    }
  }

  @Override
  public void log(String event, DDSpan span) {
    this.log(Collections.singletonMap("event", event), span);
  }

  @Override
  public void log(long timestampMicroseconds, String event, DDSpan span) {
    this.log(timestampMicroseconds, Collections.singletonMap("event", event), span);
  }

  private boolean extractError(final Map<String, ?> map, DDSpan span) {
    if (map.get(ERROR_OBJECT) instanceof Throwable) {
      // if custom instrumentation don't use setErrorMeta
      if (!map.containsKey(ERROR_KIND) && !map.containsKey(MESSAGE) && !map.containsKey(STACK)) {
        final Throwable error = (Throwable) map.get(ERROR_OBJECT);
        span.setErrorMeta(error);
        return true;
      }
    } else if (map.get(MESSAGE) instanceof String) {
      span.setTag(DDTags.ERROR_MSG, (String) map.get(MESSAGE));
      return true;
    }
    return false;
  }
}
