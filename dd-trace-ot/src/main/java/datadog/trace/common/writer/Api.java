package datadog.trace.common.writer;

import datadog.opentracing.DDSpan;
import java.util.List;

/** Common interface between the DDApi and ZipkinV2Api senders. */
public interface Api {
  boolean sendTraces(List<List<DDSpan>> traces);
}
