// Modified by SignalFx
package datadog.trace.instrumentation.lettuce;

import static io.opentracing.log.Fields.ERROR_OBJECT;

import io.lettuce.core.ConnectionFuture;
import io.lettuce.core.RedisURI;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.util.Collections;
import net.bytebuddy.asm.Advice;

public class ConnectionFutureAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static Scope startSpan(@Advice.Argument(1) final RedisURI redisURI) {
    final int redisPort = redisURI.getPort();
    final String redisHost = redisURI.getHost();
    final String url = redisHost + ":" + redisPort + "/" + redisURI.getDatabase();
    final Scope scope = GlobalTracer.get().buildSpan("CONNECT:" + url).startActive(false);

    final Span span = scope.span();
    Tags.PEER_PORT.set(span, redisPort);
    Tags.PEER_HOSTNAME.set(span, redisHost);
    Tags.DB_TYPE.set(span, LettuceInstrumentationUtil.SERVICE_NAME);
    Tags.SPAN_KIND.set(span, Tags.SPAN_KIND_CLIENT);
    Tags.COMPONENT.set(span, LettuceInstrumentationUtil.COMPONENT_NAME);

    span.setTag("db.redis.url", url);
    span.setTag("db.redis.dbIndex", redisURI.getDatabase());
    Tags.DB_STATEMENT.set(span, "CONNECT:" + url);

    return scope;
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Enter final Scope scope,
      @Advice.Thrown final Throwable throwable,
      @Advice.Return final ConnectionFuture connectionFuture) {
    if (throwable != null) {
      final Span span = scope.span();
      Tags.ERROR.set(span, true);
      span.log(Collections.singletonMap(ERROR_OBJECT, throwable));
      scope.close();
      return;
    }

    // close spans on error or normal completion
    connectionFuture.handleAsync(new LettuceAsyncBiFunction<>(scope.span()));
    scope.close();
  }
}
