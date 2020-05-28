// Modified by SignalFx
package datadog.trace.common.writer;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import datadog.opentracing.DDSpan;
import datadog.trace.api.Config;
import datadog.trace.api.DDSpanTypes;
import datadog.trace.api.DDTags;
import datadog.trace.api.Ids;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;
import lombok.extern.slf4j.Slf4j;

/** Zipkin V2 JSON HTTP encoder/sender. Follows a similar pattern to DDApi. */
@Slf4j
public class ZipkinV2Api implements Api {

  private static final JsonFactory JSON_FACTORY = new JsonFactory();
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private final String traceEndpoint;
  private static final int recordedValueMaxLength = Config.get().getRecordedValueMaxLength();
  private static final boolean gzipContentEncoding = Config.get().isZipkinGZIPContentEncoding();

  // Used to throttle logging when spans can't be sent
  private volatile long nextAllowedLogTime = 0;
  private static final long MILLISECONDS_BETWEEN_ERROR_LOG = TimeUnit.MINUTES.toMillis(3);

  ZipkinV2Api(final String host, final int port, final String path, final boolean useHTTPS) {
    String portStr = ":" + String.valueOf(port);
    if ((useHTTPS && port == 443) || (!useHTTPS && port == 80)) {
      portStr = "";
    }
    traceEndpoint = (useHTTPS ? "https" : "http") + "://" + host + portStr + path;
  }

  @Override
  public Response sendTraces(final List<List<DDSpan>> traces) {
    final List<SerializedBuffer> serializedTraces = new ArrayList<>(traces.size());
    int sizeInBytes = 0;
    for (final List<DDSpan> trace : traces) {
      try {
        final SerializedBuffer serializedTrace = serializeTrace(trace);
        sizeInBytes += serializedTrace.length();
        serializedTraces.add(serializedTrace);
      } catch (final IOException e) {
        log.warn("Error serializing trace", e);
      }
    }

    return sendSerializedTraces(serializedTraces.size(), sizeInBytes, serializedTraces);
  }

  @Override
  public SerializedBuffer serializeTrace(final List<DDSpan> trace) throws IOException {
    final StreamingSerializedBuffer stream = new StreamingSerializedBuffer(trace.size() * 128);
    final JsonGenerator jsonGenerator = JSON_FACTORY.createGenerator(stream, JsonEncoding.UTF8);

    jsonGenerator.writeStartArray();
    for (int i = 0; i < trace.size(); i++) {
      writeSpan(trace.get(i), jsonGenerator);
    }
    jsonGenerator.writeEndArray();
    jsonGenerator.close();
    return stream;
  }

  @Override
  public Response sendSerializedTraces(
      final int representativeCount,
      final Integer sizeInBytes,
      final List<SerializedBuffer> traces) {
    try {
      final HttpURLConnection httpCon = getHttpURLConnection(traceEndpoint);

      try (OutputStream out =
          gzipContentEncoding
              ? new GZIPOutputStream(httpCon.getOutputStream())
              : httpCon.getOutputStream()) {

        int traceCount = 0;

        out.write('[');
        for (final SerializedBuffer trace : traces) {
          traceCount++;
          if (trace.length() == 2) {
            // empty trace
            continue;
          }
          // don't write nested array brackets
          trace.writeTo(out, 1, trace.length() - 2);

          // don't write comma for final span
          if (traceCount != traces.size()) {
            out.write(',');
          }
        }
        out.write(']');
      }

      final int responseCode = httpCon.getResponseCode();

      final StringBuilder sb = new StringBuilder();
      try (BufferedReader responseReader =
          new BufferedReader(
              new InputStreamReader(httpCon.getInputStream(), StandardCharsets.UTF_8))) {

        String line;
        while ((line = responseReader.readLine()) != null) {
          sb.append(line);
        }
      }
      String responseContent = sb.toString();

      if (responseCode != 200 && responseCode != 202) {
        if (log.isDebugEnabled()) {
          log.debug(
              "Error while sending {} of {} traces to {}. Status: {}, Response: {}",
              traces.size(),
              representativeCount,
              traceEndpoint,
              responseCode,
              responseContent);
        } else if (nextAllowedLogTime < System.currentTimeMillis()) {
          nextAllowedLogTime = System.currentTimeMillis() + MILLISECONDS_BETWEEN_ERROR_LOG;
          log.warn(
              "Error while sending {} of {} traces. Status: {}, Response: {} (going silent for {} minutes)",
              traces.size(),
              representativeCount,
              responseCode,
              responseContent,
              TimeUnit.MILLISECONDS.toMinutes(MILLISECONDS_BETWEEN_ERROR_LOG));
        }
        return Response.failed(responseCode);
      }

      log.debug("Successfully sent {} of {} traces.", traces.size(), representativeCount);

      return Response.success(responseCode, responseContent);
    } catch (final IOException e) {
      if (log.isDebugEnabled()) {
        log.debug(
            "Error while sending " + traces.size() + " of " + representativeCount + " traces.", e);
      } else if (nextAllowedLogTime < System.currentTimeMillis()) {
        nextAllowedLogTime = System.currentTimeMillis() + MILLISECONDS_BETWEEN_ERROR_LOG;
        log.warn(
            "Error while sending {} of {} traces to {}. {}: {} (going silent for {} minutes)",
            traces.size(),
            representativeCount,
            traceEndpoint,
            e.getClass().getName(),
            e.getMessage(),
            TimeUnit.MILLISECONDS.toMinutes(MILLISECONDS_BETWEEN_ERROR_LOG));
      }
      return Response.failed(e);
    }
  }

  private void writeIdField(
      final JsonGenerator jsonGenerator, final String key, final String decimalId)
      throws IOException {
    jsonGenerator.writeFieldName(key);
    final char[] asHex = Ids.idToHexChars(decimalId);
    jsonGenerator.writeString(asHex, 0, asHex.length);
  }

  private void writeSpan(final DDSpan span, final JsonGenerator jsonGenerator) throws IOException {
    final String spanKind = deriveKind(span);
    final String spanName = getSpanName(span, spanKind);

    jsonGenerator.writeStartObject();
    writeIdField(jsonGenerator, "id", span.getSpanId());
    jsonGenerator.writeStringField("name", spanName);
    writeIdField(jsonGenerator, "traceId", span.getTraceId());
    final String parentId = span.getParentId();
    if (!parentId.equals("0")) {
      writeIdField(jsonGenerator, "parentId", span.getParentId());
    }

    if (!Strings.isNullOrEmpty(spanKind)) {
      jsonGenerator.writeStringField("kind", spanKind);
    }

    jsonGenerator.writeObjectFieldStart("localEndpoint");
    jsonGenerator.writeStringField("serviceName", span.getServiceName());
    jsonGenerator.writeEndObject();

    // DDSpan outputs time in nanoseconds and Zipkin is microseconds.
    jsonGenerator.writeNumberField("timestamp", span.getStartTime() / 1000);
    // Same units as timestamp
    jsonGenerator.writeNumberField("duration", span.getDurationNano() / 1000);

    jsonGenerator.writeObjectFieldStart("tags");
    final Map<String, Object> tags = span.getTags();
    for (final String key : tags.keySet()) {
      if (DDTags.SPAN_KIND.equals(key)) {
        continue;
      }
      // Zipkin tags are always string values
      String value = truncatedString(tags.get(key).toString());

      jsonGenerator.writeStringField(key, value);
    }

    final String resourceName = span.getResourceName();
    if (!spanName.equals(resourceName)) {
      jsonGenerator.writeStringField(DDTags.RESOURCE_NAME, resourceName);
    }
    jsonGenerator.writeEndObject();

    jsonGenerator.writeArrayFieldStart("annotations");
    final List<AbstractMap.SimpleEntry<Long, Map<String, ?>>> logs = span.getLogs();
    if (!logs.isEmpty()) {
      for (AbstractMap.SimpleEntry<Long, Map<String, ?>> item : logs) {
        jsonGenerator.writeStartObject();
        try {
          final Long timestamp = item.getKey();
          JsonNode value = OBJECT_MAPPER.valueToTree(item.getValue());
          String encodedValue = truncatedString(OBJECT_MAPPER.writeValueAsString(value));
          jsonGenerator.writeNumberField("timestamp", timestamp);
          jsonGenerator.writeStringField("value", encodedValue);
        } catch (JsonProcessingException e) {
          log.warn("Failed creating annotation");
          continue;
        }
        jsonGenerator.writeEndObject();
      }
    }
    jsonGenerator.writeEndArray();
    jsonGenerator.writeEndObject();
  }

  private String deriveKind(DDSpan span) {
    Map<String, Object> tags = span.getTags();
    if (tags.containsKey(DDTags.SPAN_KIND)) {
      Object kindObj = tags.get(DDTags.SPAN_KIND);
      if (kindObj instanceof String) {
        return ((String) kindObj).toUpperCase();
      }
    }

    // Maybe look at span.getType() if kind tag isn't there
    if (!Strings.isNullOrEmpty(span.getSpanType())) {
      switch (span.getSpanType()) {
        case DDSpanTypes.HTTP_CLIENT:
          return DDTags.SPAN_KIND_CLIENT;
      }
    }
    return null;
  }

  /**
   * In order to have more informative operation names for web frameworks, we take advantage of the
   * URLAsResourceName-normalized resource name tag value, and update the operation name.
   *
   * <p>If the (updated) spanNode's name doesn't match the resource name, set the resource.name tag,
   * as it likely contains worthwhile information.
   */
  private String getSpanName(final DDSpan span, final String spanKind) {
    String resourceName = span.getResourceName();

    if (!Strings.isNullOrEmpty(resourceName)
        && ((!Strings.isNullOrEmpty(spanKind) && spanKind.equalsIgnoreCase(DDTags.SPAN_KIND_SERVER))
            || (!Strings.isNullOrEmpty(span.getSpanType())
                && span.getSpanType().equalsIgnoreCase(DDSpanTypes.HTTP_SERVER)))) {
      return resourceName;
    }

    return span.getOperationName();
  }

  private static HttpURLConnection getHttpURLConnection(final String endpoint) throws IOException {
    final HttpURLConnection httpCon;
    final URL url = new URL(endpoint);
    httpCon = (HttpURLConnection) url.openConnection();
    httpCon.setReadTimeout(30 * 1000);
    httpCon.setConnectTimeout(30 * 1000);
    httpCon.setDoOutput(true);
    httpCon.setDoInput(true);
    httpCon.setRequestMethod("POST");
    httpCon.setRequestProperty("Content-Type", "application/json");
    if (gzipContentEncoding) {
      httpCon.setRequestProperty("Content-Encoding", "gzip");
    }

    return httpCon;
  }

  private String truncatedString(String value) {
    String encodedValue = value;
    if (encodedValue.length() > recordedValueMaxLength) {
      encodedValue = encodedValue.substring(0, recordedValueMaxLength) + "[...]";
    }
    return encodedValue;
  }

  @Override
  public String toString() {
    return "ZipkinV2Api { traceEndpoint=" + traceEndpoint + " }";
  }
}
