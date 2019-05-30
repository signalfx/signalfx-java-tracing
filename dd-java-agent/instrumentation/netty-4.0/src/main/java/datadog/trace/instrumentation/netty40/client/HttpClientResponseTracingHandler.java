// Modified by SignalFx
package datadog.trace.instrumentation.netty40.client;

import static datadog.trace.instrumentation.netty40.client.NettyHttpClientDecorator.DECORATE;

import datadog.trace.context.TraceScope;
import datadog.trace.instrumentation.netty40.AttributeKeys;
import datadog.trace.instrumentation.netty40.NettyUtils;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpResponse;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.util.GlobalTracer;

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
          DECORATE.onError(span, throwable);
          try {
            int status = ((HttpResponse) msg).getStatus().code();
            NettyUtils.setClientSpanHttpStatus(span, status);
          } catch (final Throwable ex) {
            // Unable to access status code from response.  No action needed.
          }
          DECORATE.beforeFinish(span);
          span.finish(); // Finish the span manually since finishSpanOnClose was false
          throw throwable;
        }
      }

      if (finishSpan) {
        NettyUtils.setClientSpanHttpStatus(span, ((HttpResponse) msg).getStatus().code());
        DECORATE.beforeFinish(span);
        span.finish(); // Finish the span manually since finishSpanOnClose was false
      }
    }
  }
}
