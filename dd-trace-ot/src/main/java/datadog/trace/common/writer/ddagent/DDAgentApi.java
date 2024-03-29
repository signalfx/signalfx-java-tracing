// Modified by SignalFx
package datadog.trace.common.writer.ddagent;

import static datadog.trace.common.serialization.MsgpackFormatWriter.MSGPACK_WRITER;
import static datadog.trace.common.writer.Api.Response;

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.Types;
import datadog.common.exec.CommonTaskExecutor;
import datadog.opentracing.ContainerInfo;
import datadog.opentracing.DDSpan;
import datadog.opentracing.DDTraceOTInfo;
import datadog.trace.common.writer.Api;
import datadog.trace.common.writer.Api.ResponseListener;
import datadog.trace.common.writer.unixdomainsockets.UnixDomainSocketFactory;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Dispatcher;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okio.BufferedSink;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessagePacker;
import org.msgpack.core.buffer.ArrayBufferOutput;

/** The API pointing to a DD agent */
@Slf4j
public class DDAgentApi implements Api {
  private static final String DATADOG_META_LANG = "Datadog-Meta-Lang";
  private static final String DATADOG_META_LANG_VERSION = "Datadog-Meta-Lang-Version";
  private static final String DATADOG_META_LANG_INTERPRETER = "Datadog-Meta-Lang-Interpreter";
  private static final String DATADOG_META_LANG_INTERPRETER_VENDOR =
      "Datadog-Meta-Lang-Interpreter-Vendor";
  private static final String DATADOG_META_TRACER_VERSION = "Datadog-Meta-Tracer-Version";
  private static final String DATADOG_CONTAINER_ID = "Datadog-Container-ID";
  private static final String X_DATADOG_TRACE_COUNT = "X-Datadog-Trace-Count";

  private static final int HTTP_TIMEOUT = 1; // 1 second for conenct/read/write operations
  private static final String TRACES_ENDPOINT_V3 = "v0.3/traces";
  private static final String TRACES_ENDPOINT_V4 = "v0.4/traces";
  private static final long MILLISECONDS_BETWEEN_ERROR_LOG = TimeUnit.MINUTES.toMillis(5);

  private final List<ResponseListener> responseListeners = new ArrayList<>();

  private volatile long nextAllowedLogTime = 0;

  private static final JsonAdapter<Map<String, Map<String, Number>>> RESPONSE_ADAPTER =
      new Moshi.Builder()
          .build()
          .adapter(
              Types.newParameterizedType(
                  Map.class,
                  String.class,
                  Types.newParameterizedType(Map.class, String.class, Double.class)));
  private static final MediaType MSGPACK = MediaType.get("application/msgpack");

  private final String host;
  private final int port;
  private final String unixDomainSocketPath;
  private OkHttpClient httpClient;
  private HttpUrl tracesUrl;

  public DDAgentApi(final String host, final int port, final String unixDomainSocketPath) {
    this.host = host;
    this.port = port;
    this.unixDomainSocketPath = unixDomainSocketPath;
  }

  @Override
  public void addResponseListener(final ResponseListener listener) {
    if (!responseListeners.contains(listener)) {
      responseListeners.add(listener);
    }
  }

  /**
   * Send traces to the DD agent
   *
   * @param traces the traces to be sent
   * @return a Response object -- encapsulating success of communication, sending, and result
   *     parsing
   */
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

        // TODO: DQH - Incorporate the failed serialization into the Response object???
      }
    }

    return sendSerializedTraces(serializedTraces.size(), sizeInBytes, serializedTraces);
  }

  @Override
  public SerializedBuffer serializeTrace(final List<DDSpan> trace) throws IOException {
    // TODO: reuse byte array buffer
    final ArrayBufferOutput output = new ArrayBufferOutput();
    final MessagePacker packer = MessagePack.newDefaultPacker(output);
    MSGPACK_WRITER.writeTrace(trace, packer);
    packer.flush();
    return new PredeterminedByteArraySerializedBuffer(output.toByteArray());
  }

  @Override
  public Response sendSerializedTraces(
      final int representativeCount,
      final Integer sizeInBytes,
      final List<SerializedBuffer> traces) {
    if (httpClient == null) {
      detectEndpointAndBuildClient();
    }

    try {
      final RequestBody body =
          new RequestBody() {
            @Override
            public MediaType contentType() {
              return MSGPACK;
            }

            @Override
            public long contentLength() {
              final int traceCount = traces.size();
              // Need to allocate additional to handle MessagePacker.packArrayHeader
              if (traceCount < (1 << 4)) {
                return sizeInBytes + 1; // byte
              } else if (traceCount < (1 << 16)) {
                return sizeInBytes + 3; // byte + short
              } else {
                return sizeInBytes + 5; // byte + int
              }
            }

            @Override
            public void writeTo(final BufferedSink sink) throws IOException {
              final OutputStream out = sink.outputStream();
              final MessagePacker packer = MessagePack.newDefaultPacker(out);
              packer.packArrayHeader(traces.size());
              for (final SerializedBuffer trace : traces) {
                packer.writePayload(trace.toByteArray());
              }
              packer.close();
              out.close();
            }
          };
      final Request request =
          prepareRequest(tracesUrl)
              .addHeader(X_DATADOG_TRACE_COUNT, String.valueOf(representativeCount))
              .put(body)
              .build();

      try (final okhttp3.Response response = httpClient.newCall(request).execute()) {
        if (response.code() != 200) {
          if (log.isDebugEnabled()) {
            log.debug(
                "Error while sending {} of {} traces to the DD agent. Status: {}, Response: {}, Body: {}",
                traces.size(),
                representativeCount,
                response.code(),
                response.message(),
                response.body().string());
          } else if (nextAllowedLogTime < System.currentTimeMillis()) {
            nextAllowedLogTime = System.currentTimeMillis() + MILLISECONDS_BETWEEN_ERROR_LOG;
            log.warn(
                "Error while sending {} of {} traces to the DD agent. Status: {} {} (going silent for {} minutes)",
                traces.size(),
                representativeCount,
                response.code(),
                response.message(),
                TimeUnit.MILLISECONDS.toMinutes(MILLISECONDS_BETWEEN_ERROR_LOG));
          }
          return Response.failed(response.code());
        }

        log.debug(
            "Successfully sent {} of {} traces to the DD agent.",
            traces.size(),
            representativeCount);

        final String responseString = response.body().string().trim();
        try {
          if (!"".equals(responseString) && !"OK".equalsIgnoreCase(responseString)) {
            final Map<String, Map<String, Number>> parsedResponse =
                RESPONSE_ADAPTER.fromJson(responseString);
            final String endpoint = tracesUrl.toString();

            for (final Api.ResponseListener listener : responseListeners) {
              listener.onResponse(endpoint, parsedResponse);
            }
          }
          return Response.success(response.code());
        } catch (final IOException e) {
          log.debug("Failed to parse DD agent response: " + responseString, e);

          return Response.success(response.code(), e);
        }
      }
    } catch (final IOException e) {
      if (log.isDebugEnabled()) {
        log.debug(
            "Error while sending "
                + traces.size()
                + " of "
                + representativeCount
                + " traces to the DD agent.",
            e);
      } else if (nextAllowedLogTime < System.currentTimeMillis()) {
        nextAllowedLogTime = System.currentTimeMillis() + MILLISECONDS_BETWEEN_ERROR_LOG;
        log.warn(
            "Error while sending {} of {} traces to the DD agent. {}: {} (going silent for {} minutes)",
            traces.size(),
            representativeCount,
            e.getClass().getName(),
            e.getMessage(),
            TimeUnit.MILLISECONDS.toMinutes(MILLISECONDS_BETWEEN_ERROR_LOG));
      }
      return Response.failed(e);
    }
  }

  private static final byte[] EMPTY_LIST = new byte[] {MessagePack.Code.FIXARRAY_PREFIX};

  private static boolean endpointAvailable(
      final HttpUrl url, final String unixDomainSocketPath, final boolean retry) {
    try {
      final OkHttpClient client = buildHttpClient(unixDomainSocketPath);
      final RequestBody body = RequestBody.create(MSGPACK, EMPTY_LIST);
      final Request request = prepareRequest(url).put(body).build();

      try (final okhttp3.Response response = client.newCall(request).execute()) {
        return response.code() == 200;
      }
    } catch (final IOException e) {
      if (retry) {
        return endpointAvailable(url, unixDomainSocketPath, false);
      }
    }
    return false;
  }

  private static OkHttpClient buildHttpClient(final String unixDomainSocketPath) {
    OkHttpClient.Builder builder = new OkHttpClient.Builder();
    if (unixDomainSocketPath != null) {
      builder = builder.socketFactory(new UnixDomainSocketFactory(new File(unixDomainSocketPath)));
    }
    return builder
        .connectTimeout(HTTP_TIMEOUT, TimeUnit.SECONDS)
        .writeTimeout(HTTP_TIMEOUT, TimeUnit.SECONDS)
        .readTimeout(HTTP_TIMEOUT, TimeUnit.SECONDS)

        // We don't do async so this shouldn't matter, but just to be safe...
        .dispatcher(new Dispatcher(CommonTaskExecutor.INSTANCE))
        .build();
  }

  private static HttpUrl getUrl(final String host, final int port, final String endPoint) {
    return new HttpUrl.Builder()
        .scheme("http")
        .host(host)
        .port(port)
        .addEncodedPathSegments(endPoint)
        .build();
  }

  private static SafeRequestBuilder prepareRequest(final HttpUrl url) {

    final SafeRequestBuilder builder =
        new SafeRequestBuilder()
            .url(url)
            .addHeader(DATADOG_META_LANG, "java")
            .addHeader(DATADOG_META_LANG_VERSION, DDTraceOTInfo.JAVA_VERSION)
            .addHeader(DATADOG_META_LANG_INTERPRETER, DDTraceOTInfo.JAVA_VM_NAME)
            .addHeader(DATADOG_META_LANG_INTERPRETER_VENDOR, DDTraceOTInfo.JAVA_VM_VENDOR)
            .addHeader(DATADOG_META_TRACER_VERSION, DDTraceOTInfo.VERSION);

    final String containerId = ContainerInfo.get().getContainerId();
    if (containerId == null) {
      return builder;
    } else {
      return builder.addHeader(DATADOG_CONTAINER_ID, containerId);
    }
  }

  private synchronized void detectEndpointAndBuildClient() {
    if (httpClient == null) {
      final HttpUrl v4Url = getUrl(host, port, TRACES_ENDPOINT_V4);
      if (endpointAvailable(v4Url, unixDomainSocketPath, true)) {
        tracesUrl = v4Url;
      } else {
        log.debug("API v0.4 endpoints not available. Downgrading to v0.3");
        tracesUrl = getUrl(host, port, TRACES_ENDPOINT_V3);
      }
      httpClient = buildHttpClient(unixDomainSocketPath);
    }
  }

  @Override
  public String toString() {
    return "DDApi { tracesUrl=" + tracesUrl + " }";
  }
}
