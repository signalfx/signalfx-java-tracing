// Modified by SignalFx
package datadog.trace.common.writer;

import datadog.opentracing.DDSpan;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import okhttp3.Response;

/** Common interface between the DDApi and ZipkinV2Api senders. */
public interface Api {
  // This structure is to avoid re-copying the serialized Zipkin json buffer to a new byte[] and
  // instead just keep it
  // in the ByteArrayOutputStream's internal buffer.
  public static interface SerializedBuffer {
    public int length();

    public void writeTo(OutputStream out, int startingIndex, int length) throws IOException;

    public byte[] toByteArray();
  }

  public static class StreamingSerializedBuffer extends ByteArrayOutputStream
      implements SerializedBuffer {
    public StreamingSerializedBuffer(int initialCapacity) {
      super(initialCapacity);
    }

    public int length() {
      return size();
    }

    public void writeTo(OutputStream out, int startingIndex, int length) throws IOException {
      out.write(buf, startingIndex, length);
    }
  }

  public static class PredeterminedByteArraySerializedBuffer implements SerializedBuffer {
    private final byte[] buf;

    public PredeterminedByteArraySerializedBuffer(byte[] buf) {
      this.buf = buf;
    }

    public void writeTo(OutputStream out, int startingIndex, int length) throws IOException {
      out.write(buf, startingIndex, length);
    }

    public int length() {
      return buf.length;
    }

    public byte[] toByteArray() {
      return buf;
    }
  }

  void addResponseListener(final ResponseListener listener);

  Response sendTraces(List<List<DDSpan>> traces);

  SerializedBuffer serializeTrace(final List<DDSpan> trace) throws IOException;

  Response sendSerializedTraces(
      final int representativeCount,
      final Integer sizeInBytes,
      final List<SerializedBuffer> traces);

  /**
   * Encapsulates an attempted response from the Datadog agent.
   *
   * <p>If communication fails or times out, the Response will NOT be successful and will lack
   * status code, but will have an exception.
   *
   * <p>If an communication occurs, the Response will have a status code and will be marked as
   * success or fail in accordance with the code.
   *
   * <p>NOTE: A successful communication may still contain an exception if there was a problem
   * parsing the response from the Datadog agent.
   */
  public static final class Response {
    /** Factory method for a successful request with a trivial response body */
    public static final Response success(final int status) {
      return new Response(true, status, null);
    }

    /** Factory method for a successful request will a malformed response body */
    public static final Response success(final int status, final Throwable exception) {
      return new Response(true, status, exception);
    }

    /** Factory method for a request that receive an error status in response */
    public static final Response failed(final int status) {
      return new Response(false, status, null);
    }

    /** Factory method for a failed communication attempt */
    public static final Response failed(final Throwable exception) {
      return new Response(false, null, exception);
    }

    private final boolean success;
    private final Integer status;
    private final Throwable exception;

    private Response(final boolean success, final Integer status, final Throwable exception) {
      this.success = success;
      this.status = status;
      this.exception = exception;
    }

    public final boolean success() {
      return success;
    }

    // TODO: DQH - In Java 8, switch to OptionalInteger
    public final Integer status() {
      return status;
    }

    // TODO: DQH - In Java 8, switch to Optional<Throwable>?
    public final Throwable exception() {
      return exception;
    }
  }

  public interface ResponseListener {
    /** Invoked after the api receives a response from the core agent. */
    void onResponse(String endpoint, Map<String, Map<String, Number>> responseJson);
  }
}
