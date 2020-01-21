// Modified by SignalFx
package datadog.trace.instrumentation.netty41;

import datadog.trace.api.Config;
import datadog.trace.instrumentation.api.AgentSpan;
import datadog.trace.instrumentation.api.Tags;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NettyUtils {

  public static final String NETTY_REWRITTEN_SERVER_STATUS_PREFIX =
      "instrumentation.netty.server.nonstandard.http.status.";
  public static final String NETTY_REWRITTEN_CLIENT_STATUS_PREFIX =
      "instrumentation.netty.client.nonstandard.http.status.";
  public static final String ORIG_HTTP_STATUS = Tags.HTTP_STATUS + ".orig";

  private NettyUtils() {}

  public static boolean setServerSpanHttpStatus(AgentSpan span, int status) {
    String name = NETTY_REWRITTEN_SERVER_STATUS_PREFIX + status;
    Boolean rewrite = Config.getBooleanSettingFromEnvironment(name, false);
    String tag = rewrite ? ORIG_HTTP_STATUS : Tags.HTTP_STATUS;
    span.setTag(tag, status);
    return rewrite;
  }

  public static boolean setClientSpanHttpStatus(AgentSpan span, int status) {
    String name = NETTY_REWRITTEN_CLIENT_STATUS_PREFIX + status;
    Boolean rewrite = Config.getBooleanSettingFromEnvironment(name, false);
    String tag = rewrite ? ORIG_HTTP_STATUS : Tags.HTTP_STATUS;
    span.setTag(tag, status);
    return rewrite;
  }
}
