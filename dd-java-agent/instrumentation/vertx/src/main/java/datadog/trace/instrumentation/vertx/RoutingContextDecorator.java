package datadog.trace.instrumentation.vertx;

import datadog.trace.agent.decorator.HttpServerDecorator;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import java.net.URI;
import java.net.URISyntaxException;

public class RoutingContextDecorator
    extends HttpServerDecorator<HttpServerRequest, HttpServerRequest, HttpServerResponse> {

  public static final RoutingContextDecorator DECORATE = new RoutingContextDecorator();

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
    return httpServerRequest.rawMethod();
  }

  @Override
  protected URI url(final HttpServerRequest httpServerRequest) throws URISyntaxException {
    return new URI(httpServerRequest.absoluteURI());
  }

  @Override
  protected String peerHostname(final HttpServerRequest httpServerRequest) {
    return httpServerRequest.host().split(":")[0];
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
}
