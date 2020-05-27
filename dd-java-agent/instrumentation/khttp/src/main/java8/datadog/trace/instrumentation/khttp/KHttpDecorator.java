// Modified by SignalFx
package datadog.trace.instrumentation.khttp;

import datadog.trace.bootstrap.instrumentation.decorator.HttpClientDecorator;
import java.net.URI;
import java.net.URISyntaxException;
import khttp.requests.Request;
import khttp.responses.Response;

public class KHttpDecorator extends HttpClientDecorator<Request, Response> {
  public static final KHttpDecorator DECORATE = new KHttpDecorator();

  @Override
  protected String[] instrumentationNames() {
    return new String[] {"khttp"};
  }

  @Override
  protected String component() {
    return "khttp";
  }

  @Override
  protected String method(final Request httpRequest) {
    return httpRequest.getMethod();
  }

  @Override
  protected URI url(final Request request) throws URISyntaxException {
    final String url = request.getUrl();
    return url == null ? null : new URI(url);
  }

  protected URI getURI(final Request request) {
    URI uri = null;
    try {
      return this.url(request);
    } catch (URISyntaxException e) {
      return null;
    }
  }

  @Override
  protected Integer status(final Response httpResponse) {
    return httpResponse.getStatusCode();
  }
}
