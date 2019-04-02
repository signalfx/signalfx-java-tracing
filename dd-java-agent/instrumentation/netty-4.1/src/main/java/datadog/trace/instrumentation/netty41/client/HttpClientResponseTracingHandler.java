// Modified by SignalFx
package datadog.trace.instrumentation.netty41.client;

import static io.opentracing.log.Fields.ERROR_OBJECT;

import datadog.trace.context.TraceScope;
import datadog.trace.instrumentation.netty41.AttributeKeys;
import datadog.trace.instrumentation.netty41.NettyUtils;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpResponse;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.util.Collections;

public class HttpClientResponseTracingHandler extends ChannelInboundHandlerAdapter {

  @Override
  public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
    final Span span = ctx.channel().attr(AttributeKeys.CLIENT_ATTRIBUTE_KEY).get();
    if (span == null) {
      ctx.fireChannelRead(msg);
      return;
    }

    try (final Scope scope = GlobalTracer.get().scopeManager().activate(span, false)) {
      final boolean finishSpan = msg instanceof HttpResponse;

      if (scope instanceof TraceScope) {
        ((TraceScope) scope).setAsyncPropagation(true);
      }
      try {
        ctx.fireChannelRead(msg);
      } catch (final Throwable throwable) {
        if (finishSpan) {
          Tags.ERROR.set(span, Boolean.TRUE);
          span.log(Collections.singletonMap(ERROR_OBJECT, throwable));
          try {
            int status = ((HttpResponse) msg).status().code();
            NettyUtils.setClientSpanHttpStatus(span, status);
          } catch (final Throwable ex) {
            // Unable to access status code from response.  No action needed.
          }
          span.finish(); // Finish the span manually since finishSpanOnClose was false
          throw throwable;
        }
      }

      if (finishSpan) {
        NettyUtils.setClientSpanHttpStatus(span, ((HttpResponse) msg).status().code());
        span.finish(); // Finish the span manually since finishSpanOnClose was false
      }
    }
  }
}
