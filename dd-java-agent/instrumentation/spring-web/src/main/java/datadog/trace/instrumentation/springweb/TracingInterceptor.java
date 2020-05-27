// Modified by SignalFx
package datadog.trace.instrumentation.springweb;

import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.propagate;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.startSpan;
import static datadog.trace.instrumentation.springweb.InjectAdapter.SETTER;

import datadog.trace.api.DDSpanTypes;
import datadog.trace.api.DDTags;
import datadog.trace.bootstrap.instrumentation.api.AgentScope;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.bootstrap.instrumentation.api.Tags;
import java.io.IOException;
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

  @Override
  public ClientHttpResponse intercept(
      HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
    final AgentSpan span =
        startSpan("http.request")
            .setTag(Tags.SPAN_KIND, Tags.SPAN_KIND_CLIENT)
            .setTag(Tags.COMPONENT, "rest-template")
            .setTag(Tags.HTTP_METHOD, request.getMethod().toString())
            .setTag(Tags.HTTP_URL, request.getURI().toString())
            .setTag(Tags.PEER_PORT, request.getURI().getPort())
            .setTag(Tags.PEER_HOSTNAME, request.getURI().getHost())
            .setTag(DDTags.SPAN_TYPE, DDSpanTypes.HTTP_CLIENT);

    propagate().inject(span, request.getHeaders(), SETTER);

    final AgentScope scope = activateSpan(span, true);

    ClientHttpResponse response = null;
    try {
      response = execution.execute(request, body);
      return response;
    } catch (final Throwable ex) {
      span.setTag(Tags.ERROR, true);
      span.addThrowable(ex);
      throw ex;
    } finally {
      if (response != null) {
        try {
          span.setTag(Tags.HTTP_STATUS, response.getRawStatusCode());
        } catch (final Throwable scExc) {
        }
      }
      scope.close();
    }
  }
}
