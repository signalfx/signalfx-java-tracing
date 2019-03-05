package datadog.trace.instrumentation.okhttp3;

import static io.opentracing.log.Fields.ERROR_OBJECT;

import datadog.trace.api.Config;
import datadog.trace.api.DDTags;
import io.opentracing.Span;
import io.opentracing.tag.Tags;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.Collections;
import okhttp3.Connection;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Span decorator to add tags, logs and operation name.
 *
 * @author Pavol Loffay
 */
public interface OkHttpClientSpanDecorator {

  /**
   * Decorate span before a request is made.
   *
   * @param request request
   * @param span span
   */
  void onRequest(Request request, Span span);

  /**
   * Decorate span on an error e.g. {@link java.net.UnknownHostException} or any exception in
   * interceptor.
   *
   * @param throwable exception
   * @param span span
   */
  void onError(Throwable throwable, Span span);

  /**
   * This is invoked after {@link okhttp3.Interceptor.Chain#proceed(Request)} in network
   * interceptor. In this method it is possible to capture server address, log redirects etc.
   *
   * @param connection connection
   * @param response response
   * @param span span
   */
  void onResponse(Connection connection, Response response, Span span);

  /**
   * Decorator which adds standard HTTP and peer tags to the span.
   *
   * <p>
   *
   * <p>On error it adds {@link Tags#ERROR} with log representing exception and on redirects adds
   * log entries with peer tags.
   */
  OkHttpClientSpanDecorator STANDARD_TAGS =
      new OkHttpClientSpanDecorator() {
        @Override
        public void onRequest(final Request request, final Span span) {
          Tags.COMPONENT.set(span, TracingCallFactory.COMPONENT_NAME);
          Tags.HTTP_METHOD.set(span, request.method());
          Tags.HTTP_URL.set(span, request.url().toString());
          if (Config.get().isHttpClientSplitByDomain()) {
            span.setTag(DDTags.SERVICE_NAME, request.url().host());
          }
        }

        @Override
        public void onError(final Throwable throwable, final Span span) {
          Tags.ERROR.set(span, Boolean.TRUE);
          span.log(Collections.singletonMap(ERROR_OBJECT, throwable));
        }

        @Override
        public void onResponse(
            final Connection connection, final Response response, final Span span) {
          final InetAddress inetAddress = connection.socket().getInetAddress();

          Tags.HTTP_STATUS.set(span, response.code());
          Tags.PEER_HOSTNAME.set(span, inetAddress.getHostName());
          Tags.PEER_PORT.set(span, connection.socket().getPort());

          String ipvKey = Tags.PEER_HOST_IPV4.getKey();
          if (inetAddress instanceof Inet6Address) {
            ipvKey = Tags.PEER_HOST_IPV6.getKey();
          }
          span.setTag(ipvKey, inetAddress.getHostAddress());
        }
      };
}
