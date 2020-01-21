// Modified by SignalFx
package datadog.trace.common.writer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Strings;
import datadog.opentracing.DDSpan;
import datadog.trace.api.DDSpanTypes;
import datadog.trace.api.DDTags;
import datadog.trace.common.util.Ids;
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
import lombok.extern.slf4j.Slf4j;

/** Zipkin V2 JSON HTTP encoder/sender. Follows a similar pattern to DDApi. */
@Slf4j
public class ZipkinV2Api implements Api {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private final String traceEndpoint;

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
    final List<byte[]> serializedTraces = new ArrayList<>(traces.size());
    int sizeInBytes = 0;
    for (final List<DDSpan> trace : traces) {
      try {
        final byte[] serializedTrace = serializeTrace(trace);
        sizeInBytes += serializedTrace.length;
        serializedTraces.add(serializedTrace);
      } catch (final JsonProcessingException e) {
        log.warn("Error serializing trace", e);
      }
    }

    return sendSerializedTraces(serializedTraces.size(), sizeInBytes, serializedTraces);
  }

  @Override
  public byte[] serializeTrace(final List<DDSpan> trace) throws JsonProcessingException {
    ArrayNode spanArr = OBJECT_MAPPER.createArrayNode();
    for (DDSpan span : trace) {
      spanArr.add(encodeSpan(span));
    }
    return OBJECT_MAPPER.writeValueAsBytes(spanArr);
  }

  @Override
  public Response sendSerializedTraces(
      final int representativeCount, final Integer sizeInBytes, final List<byte[]> traces) {
    try {
      final HttpURLConnection httpCon = getHttpURLConnection(traceEndpoint);

      try (OutputStream out = httpCon.getOutputStream()) {

        int traceCount = 0;

        out.write('[');
        for (final byte[] trace : traces) {
          traceCount++;
          if (trace.length == 2) {
            // empty trace
            continue;
          }
          // don't write nested array brackets
          out.write(trace, 1, trace.length - 2);

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

      if (responseCode != 200) {
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

  /**
   * We want to avoid having to import Zipkin code here so construct its V2 JSON format manually.
   *
   * <p>Just flatten out the lists of spans to a single output list.
   */
  private ArrayNode encodeTraces(final List<List<DDSpan>> traces) {
    ArrayNode spanArr = OBJECT_MAPPER.createArrayNode();
    for (List<DDSpan> trace : traces) {
      for (DDSpan span : trace) {
        spanArr.add(encodeSpan(span));
      }
    }
    return spanArr;
  }

  private ObjectNode encodeSpan(final DDSpan span) {
    ObjectNode spanNode = OBJECT_MAPPER.createObjectNode();
    spanNode.put("id", Ids.idToHex(span.getSpanId()));
    spanNode.put("name", span.getOperationName());
    spanNode.put("traceId", Ids.idToHex(span.getTraceId()));
    spanNode.put("parentId", Ids.idToHex(span.getParentId()));
    spanNode.put("kind", deriveKind(span));

    ObjectNode localEndpointNode = spanNode.putObject("localEndpoint");
    localEndpointNode.put("serviceName", span.getServiceName());

    // DDSpan outputs time in nanoseconds and Zipkin is microseconds.
    spanNode.put("timestamp", span.getStartTime() / 1000);
    // Same units as timestamp
    spanNode.put("duration", span.getDurationNano() / 1000);

    ObjectNode tagNode = spanNode.putObject("tags");
    for (Map.Entry<String, Object> tag : span.getTags().entrySet()) {
      if (DDTags.SPAN_KIND.equals(tag.getKey())) {
        continue;
      }
      // Zipkin tags are always string values
      tagNode.put(tag.getKey(), tag.getValue().toString());
    }

    updateFromResourceName(spanNode, tagNode, span);

    ArrayNode annotations = spanNode.putArray("annotations");
    for (AbstractMap.SimpleEntry<Long, Map<String, ?>> item : span.getLogs()) {
      final ObjectNode annotation = OBJECT_MAPPER.createObjectNode();
      annotation.put("timestamp", item.getKey());
      JsonNode value = OBJECT_MAPPER.valueToTree(item.getValue());
      try {
        String encodedValue = OBJECT_MAPPER.writeValueAsString(value);
        annotation.put("value", encodedValue);
      } catch (JsonProcessingException e) {
        log.warn("Failed creating annotation");
        continue;
      }
      annotations.add(annotation);
    }

    return spanNode;
  }

  private String deriveKind(DDSpan span) {
    Map<String, Object> tags = span.getTags();
    if (tags.containsKey(DDTags.SPAN_KIND)) {
      Object kindObj = tags.get(DDTags.SPAN_KIND);
      if (kindObj instanceof String) {
        return (String) kindObj;
      }
    }

    // Maybe look at span.getType() if kind tag isn't there
    if (!Strings.isNullOrEmpty(span.getSpanType())) {
      switch (span.getSpanType()) {
        case DDSpanTypes.HTTP_CLIENT:
          return DDTags.SPAN_KIND_CLIENT;
        case DDSpanTypes.HTTP_SERVER:
          return DDTags.SPAN_KIND_SERVER;
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
  private void updateFromResourceName(ObjectNode spanNode, ObjectNode tagNode, DDSpan span) {
    String resourceName = span.getResourceName();
    if (Strings.isNullOrEmpty(resourceName)) {
      return;
    }

    final String spanKind = spanNode.get("kind").textValue();
    if (!Strings.isNullOrEmpty(spanKind)
        && spanKind.toLowerCase().equals(DDTags.SPAN_KIND_SERVER.toLowerCase())) {
      spanNode.put("name", resourceName);
    }

    String spanName = spanNode.get("name").textValue();
    if (!spanName.equals(resourceName)) {
      tagNode.put(DDTags.RESOURCE_NAME, span.getResourceName());
    }
  }

  private static HttpURLConnection getHttpURLConnection(final String endpoint) throws IOException {
    final HttpURLConnection httpCon;
    final URL url = new URL(endpoint);
    httpCon = (HttpURLConnection) url.openConnection();
    httpCon.setDoOutput(true);
    httpCon.setDoInput(true);
    httpCon.setRequestMethod("POST");
    httpCon.setRequestProperty("Content-Type", "application/json");

    return httpCon;
  }

  @Override
  public String toString() {
    return "ZipkinV2Api { traceEndpoint=" + traceEndpoint + " }";
  }
}
