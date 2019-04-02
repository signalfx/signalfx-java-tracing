package datadog.trace.instrumentation.netty40;

import datadog.trace.api.Config;
import io.opentracing.Span;
import io.opentracing.tag.IntTag;
import io.opentracing.tag.Tags;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NettyUtils {

  public static final String NETTY_REWRITTEN_SERVER_STATUS_PREFIX =
      "instrumentation.netty.server.nonstandard.http.status.";
  public static final String NETTY_REWRITTEN_CLIENT_STATUS_PREFIX =
      "instrumentation.netty.client.nonstandard.http.status.";
  public static final IntTag ORIG_HTTP_STATUS = new IntTag(Tags.HTTP_STATUS.getKey() + ".orig");

  private NettyUtils() {}

  public static void setServerSpanHttpStatus(Span span, int status) {
    String name = NETTY_REWRITTEN_SERVER_STATUS_PREFIX + status;
    Boolean rewrite = Config.getBooleanSettingFromEnvironment(name, false);
    (rewrite ? ORIG_HTTP_STATUS : Tags.HTTP_STATUS).set(span, status);
  }

  public static void setClientSpanHttpStatus(Span span, int status) {
    String name = NETTY_REWRITTEN_CLIENT_STATUS_PREFIX + status;
    Boolean rewrite = Config.getBooleanSettingFromEnvironment(name, false);
    (rewrite ? ORIG_HTTP_STATUS : Tags.HTTP_STATUS).set(span, status);
  }
}
