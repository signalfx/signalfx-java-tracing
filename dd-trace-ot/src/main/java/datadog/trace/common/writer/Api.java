package datadog.trace.common.writer;

import com.fasterxml.jackson.core.JsonProcessingException;
import datadog.opentracing.DDSpan;
import java.util.List;

/** Common interface between the DDApi and ZipkinV2Api senders. */
public interface Api {
  boolean sendTraces(List<List<DDSpan>> traces);

  byte[] serializeTrace(final List<DDSpan> trace) throws JsonProcessingException;

  boolean sendSerializedTraces(
      final int representativeCount, final Integer sizeInBytes, final List<byte[]> traces);
}
