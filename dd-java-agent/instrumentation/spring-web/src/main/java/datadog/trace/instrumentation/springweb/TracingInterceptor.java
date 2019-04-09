package datadog.trace.instrumentation.springweb;

import static io.opentracing.log.Fields.ERROR_OBJECT;

import datadog.trace.api.DDSpanTypes;
import datadog.trace.api.DDTags;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.tag.Tags;
import java.io.IOException;
import java.util.Collections;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * RestTemplate interceptor to trace client requests. Interceptor adds span context into outgoing
 * requests. This instrumentation fails to properly infer parent span when doing simultaneously
 * asynchronous calls.
 *
 * <p>Initialization via added interceptor in RestTemplate() constructor.
 */
public class TracingInterceptor implements ClientHttpRequestInterceptor {

  private final Tracer tracer;

  public TracingInterceptor(final Tracer tracer) {
    this.tracer = tracer;
  }

  @Override
  public ClientHttpResponse intercept(
      HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
    final Scope scope =
        tracer
            .buildSpan("http.request")
            .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
            .withTag(Tags.COMPONENT.getKey(), "rest-template")
            .withTag(Tags.HTTP_METHOD.getKey(), request.getMethod().toString())
            .withTag(Tags.HTTP_URL.getKey(), request.getURI().toString())
            .withTag(Tags.PEER_PORT.getKey(), request.getURI().getPort())
            .withTag(Tags.PEER_HOSTNAME.getKey(), request.getURI().getHost())
            .withTag(DDTags.SPAN_TYPE, DDSpanTypes.HTTP_CLIENT)
            .startActive(true);

    final Span span = scope.span();
    tracer.inject(
        span.context(), Format.Builtin.HTTP_HEADERS, new InjectAdapter(request.getHeaders()));

    ClientHttpResponse response = null;
    try {
      response = execution.execute(request, body);
      return response;
    } catch (final Throwable ex) {
      Tags.ERROR.set(span, true);
      span.log(Collections.singletonMap(ERROR_OBJECT, ex));
      throw ex;
    } finally {
      if (response != null) {
        try {
          Tags.HTTP_STATUS.set(span, response.getRawStatusCode());
        } catch (final Throwable scExc) {
        }
      }
      scope.close();
    }
  }
}
