// Modified by SignalFx
package datadog.trace.instrumentation.play26;

import datadog.trace.api.DDTags;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.bootstrap.instrumentation.api.Tags;
import datadog.trace.bootstrap.instrumentation.decorator.HttpServerDecorator;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.UndeclaredThrowableException;
import java.net.URI;
import java.net.URISyntaxException;
import lombok.extern.slf4j.Slf4j;
import play.libs.typedmap.TypedKey;
import play.api.mvc.Request;
import play.api.mvc.Result;
import play.api.routing.HandlerDef;
import play.routing.Router;
import scala.Option;

@Slf4j
public class PlayHttpServerDecorator extends HttpServerDecorator<Request, Request, Result> {
  public static final PlayHttpServerDecorator DECORATE = new PlayHttpServerDecorator();

  @Override
  protected String[] instrumentationNames() {
    return new String[] {"play"};
  }

  @Override
  protected String component() {
    return "play-action";
  }

  @Override
  protected String method(final Request httpRequest) {
    return httpRequest.method();
  }

  @Override
  protected URI url(final Request request) throws URISyntaxException {
    return new URI((request.secure() ? "https://" : "http://") + request.host() + request.uri());
  }

  @Override
  protected String peerHostIP(final Request request) {
    return request.remoteAddress();
  }

  @Override
  protected Integer peerPort(final Request request) {
    return null;
  }

  @Override
  protected Integer status(final Result httpResponse) {
    return httpResponse.header().status();
  }

  @Override
  public AgentSpan onRequest(final AgentSpan span, final Request request) {
    super.onRequest(span, request);
    if (request != null) {
      // more about routes here:
      // https://github.com/playframework/playframework/blob/master/documentation/manual/releases/release26/migration26/Migration26.md
      final Option<HandlerDef> defOption = getDefOption(Router.Attrs.HANDLER_DEF, request);
      if (defOption != null && !defOption.isEmpty()) {
        final String path = defOption.get().path();
        span.setTag(DDTags.RESOURCE_NAME, path);
      }
    }
    return span;
  }

  @Override
  public AgentSpan onError(final AgentSpan span, Throwable throwable) {
    span.setTag(Tags.HTTP_STATUS, 500);
    if (throwable != null
        // This can be moved to instanceof check when using Java 8.
        && throwable.getClass().getName().equals("java.util.concurrent.CompletionException")
        && throwable.getCause() != null) {
      throwable = throwable.getCause();
    }
    while ((throwable instanceof InvocationTargetException
            || throwable instanceof UndeclaredThrowableException)
        && throwable.getCause() != null) {
      throwable = throwable.getCause();
    }
    return super.onError(span, throwable);
  }

  private Option<HandlerDef> getDefOption(TypedKey handler, Request request) {
    Class HandlerDefClass = handler.getClass();
    Method asScalaOrUnderlying = null;

    try {
      asScalaOrUnderlying = HandlerDefClass.getMethod("asScala");
    } catch (NoSuchMethodException e) {
      try {
        asScalaOrUnderlying = HandlerDefClass.getMethod("underlying");
      } catch (NoSuchMethodException err) {
        log.error(err.getMessage());
        return null;
      }
    }

    Object typedKey = null;
    try {
      typedKey = asScalaOrUnderlying.invoke(Router.Attrs.HANDLER_DEF);
    } catch (ReflectiveOperationException e) {
      log.error(e.getMessage());
    }
    if (typedKey instanceof play.api.libs.typedmap.TypedKey) {
      return request.attrs().get((play.api.libs.typedmap.TypedKey) typedKey);
    } else {
      log.error("Unexpected type for TypedMap key: " + typedKey);
    }
    return null;
  }
}
