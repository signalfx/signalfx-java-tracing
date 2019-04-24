package datadog.trace.common.writer;

import static io.opentracing.tag.Tags.SPAN_KIND;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Strings;
import datadog.opentracing.DDSpan;
import datadog.trace.api.DDSpanTypes;
import datadog.trace.api.DDTags;
import datadog.trace.common.util.Ids;
import io.opentracing.tag.Tags;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

/** Zipkin V2 JSON HTTP encoder/sender. Follows a similar pattern to DDApi. */
@Slf4j
public class ZipkinV2Api implements Api {
  private static final ObjectMapper objectMapper = new ObjectMapper();
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
  public boolean sendTraces(final List<List<DDSpan>> traces) {
    try {
      final HttpURLConnection httpCon = getHttpURLConnection(traceEndpoint);

      final OutputStream out = httpCon.getOutputStream();
      objectMapper.writeValue(out, encodeTraces(traces));
      out.flush();
      out.close();

      final int responseCode = httpCon.getResponseCode();

      final BufferedReader responseReader =
          new BufferedReader(
              new InputStreamReader(httpCon.getInputStream(), StandardCharsets.UTF_8));
      final StringBuilder sb = new StringBuilder();

      String line;
      while ((line = responseReader.readLine()) != null) {
        sb.append(line);
      }
      responseReader.close();

      if (responseCode != 200) {
        log.warn("Bad response code sending traces to {}: {}", traceEndpoint, sb.toString());
      } else {
        log.debug("Successfully sent {} traces", traces.size());
      }
    } catch (final IOException e) {
      if (log.isDebugEnabled()) {
        log.debug("Error while sending " + traces.size() + " traces.", e);
      } else if (nextAllowedLogTime < System.currentTimeMillis()) {
        nextAllowedLogTime = System.currentTimeMillis() + MILLISECONDS_BETWEEN_ERROR_LOG;
        log.warn(
            "Error while sending {} traces to {}. {}: {} (going silent for {} minutes)",
            traces.size(),
            traceEndpoint,
            e.getClass().getName(),
            e.getMessage(),
            TimeUnit.MILLISECONDS.toMinutes(MILLISECONDS_BETWEEN_ERROR_LOG));
      }
      return false;
    }
    return true;
  }

  /**
   * We want to avoid having to import Zipkin code here so construct its V2 JSON format manually.
   *
   * <p>Just flatten out the lists of spans to a single output list.
   */
  private ArrayNode encodeTraces(final List<List<DDSpan>> traces) {
    ArrayNode spanArr = objectMapper.createArrayNode();
    for (List<DDSpan> trace : traces) {
      for (DDSpan span : trace) {
        spanArr.add(encodeSpan(span));
      }
    }
    return spanArr;
  }

  private ObjectNode encodeSpan(final DDSpan span) {
    ObjectNode spanNode = objectMapper.createObjectNode();
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
      if (Tags.SPAN_KIND.getKey().equals(tag.getKey())) {
        continue;
      }
      // Zipkin tags are always string values
      tagNode.put(tag.getKey(), tag.getValue().toString());
    }

    if (!Strings.isNullOrEmpty(span.getResourceName())) {
      tagNode.put(DDTags.RESOURCE_NAME, span.getResourceName());
    }

    updateFromResourceTag(spanNode, tagNode);

    return spanNode;
  }

  private String deriveKind(DDSpan span) {
    Map<String, Object> tags = span.getTags();
    if (tags.containsKey(SPAN_KIND.getKey())) {
      Object kindObj = tags.get(SPAN_KIND.getKey());
      if (kindObj instanceof String) {
        return (String) kindObj;
      }
    }

    // Maybe look at span.getType() if kind tag isn't there
    if (!Strings.isNullOrEmpty(span.getSpanType())) {
      switch (span.getSpanType()) {
        case DDSpanTypes.HTTP_CLIENT:
          return Tags.SPAN_KIND_CLIENT;
        case DDSpanTypes.HTTP_SERVER:
          return Tags.SPAN_KIND_SERVER;
      }
    }
    return null;
  }

  /**
   * In order to have more informative operation names for web frameworks, we take advantage of the
   * URLAsResourceName-normalized resource name tag value, and update the operation name.
   */
  private void updateFromResourceTag(ObjectNode spanNode, ObjectNode tagNode) {
    final JsonNode taggedResourceName = tagNode.get(DDTags.RESOURCE_NAME);
    if (taggedResourceName == null) {
      return;
    }

    final String resourceName = taggedResourceName.textValue();
    if (Strings.isNullOrEmpty(resourceName)) {
      return;
    }

    final String spanKind = spanNode.get("kind").textValue();
    if (!Strings.isNullOrEmpty(spanKind)
        && spanKind.toLowerCase().equals(Tags.SPAN_KIND_SERVER.toLowerCase())) {
      spanNode.put("name", resourceName);
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
