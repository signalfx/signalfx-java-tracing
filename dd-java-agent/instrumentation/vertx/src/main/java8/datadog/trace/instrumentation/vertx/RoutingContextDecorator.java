// Modified by SignalFx
package datadog.trace.instrumentation.vertx;

import datadog.trace.agent.decorator.HttpServerDecorator;
import datadog.trace.api.Config;
import datadog.trace.api.DDSpanTypes;
import io.opentracing.Span;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.TreeSet;

public class RoutingContextDecorator
    extends HttpServerDecorator<HttpServerRequest, HttpServerRequest, HttpServerResponse> {

  public static final RoutingContextDecorator DECORATE = new RoutingContextDecorator();
  private boolean setKind =
    !Config.get().integrationEnabled(new TreeSet<>(Arrays.asList("netty")), true);

  public boolean shouldSetKind() {
    return this.setKind;
  }

  @Override
  protected String[] instrumentationNames() {
    return new String[] {"vertx"};
  }

  @Override
  protected String component() {
    return "vertx";
  }

  @Override
  protected String method(final HttpServerRequest httpServerRequest) {
    return httpServerRequest.method().toString();
  }

  @Override
  protected URI url(final HttpServerRequest httpServerRequest) throws URISyntaxException {
    return new URI(httpServerRequest.absoluteURI());
  }

  @Override
  protected String peerHostname(final HttpServerRequest httpServerRequest) {
    return httpServerRequest.getHeader("Host").split(":")[0];
  }

  @Override
  protected String peerHostIP(final HttpServerRequest httpServerRequest) {
    return httpServerRequest.remoteAddress().host();
  }

  @Override
  protected Integer peerPort(final HttpServerRequest httpServerRequest) {
    return httpServerRequest.remoteAddress().port();
  }

  @Override
  protected Integer status(final HttpServerResponse httpServerResponse) {
    return httpServerResponse.getStatusCode();
  }

  @Override
  public Span afterStart(final Span span) {
    assert span != null;
    return super.afterStart(span, this.shouldSetKind());
  }

  @Override
  protected String spanType() {
    if (shouldSetKind()) {
      return DDSpanTypes.HTTP_SERVER;

    }
    return null;
  }
}
