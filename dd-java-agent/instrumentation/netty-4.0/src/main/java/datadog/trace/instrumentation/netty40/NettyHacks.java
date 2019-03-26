package datadog.trace.instrumentation.netty40;

import datadog.trace.api.Config;
import io.opentracing.Span;
import io.opentracing.tag.IntTag;
import io.opentracing.tag.Tags;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NettyHacks {

  public static final String NETTY_REWRITEN_STATUS_PREFIX = "integration.netty.hacks.rewrite.";
  public static final IntTag ORIG_HTTP_STATUS = new IntTag(Tags.HTTP_STATUS.getKey() + ".orig");

  private NettyHacks() {}

  public static void setSpanHttpStatus(Span span, int status) {
    String name = NETTY_REWRITEN_STATUS_PREFIX + status;
    boolean rewrite = Config.getBooleanSettingFromEnvironment(name, false);
    log.info("for status {}, rewrite from {} is {}", status, name, rewrite);
    (rewrite ? ORIG_HTTP_STATUS : Tags.HTTP_STATUS).set(span, status);
  }
}
