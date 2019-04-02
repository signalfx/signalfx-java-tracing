// Modified by SignalFx
package datadog.trace.instrumentation.netty41.server;

import static io.opentracing.log.Fields.ERROR_OBJECT;

import datadog.trace.instrumentation.netty41.AttributeKeys;
import datadog.trace.instrumentation.netty41.NettyUtils;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpResponse;
import io.opentracing.Span;
import io.opentracing.tag.Tags;
import java.util.Collections;

public class HttpServerResponseTracingHandler extends ChannelOutboundHandlerAdapter {

  @Override
  public void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise prm) {
    final Span span = ctx.channel().attr(AttributeKeys.SERVER_ATTRIBUTE_KEY).get();
    if (span == null || !(msg instanceof HttpResponse)) {
      ctx.write(msg, prm);
      return;
    }

    final HttpResponse response = (HttpResponse) msg;

    try {
      ctx.write(msg, prm);
    } catch (final Throwable throwable) {
      Tags.ERROR.set(span, Boolean.TRUE);
      span.log(Collections.singletonMap(ERROR_OBJECT, throwable));
      try {
        int status = response.status().code();
        NettyUtils.setServerSpanHttpStatus(span, status);
      } catch (final Throwable exc) {
        // Unable to retrieve status code
      }
      span.finish(); // Finish the span manually since finishSpanOnClose was false
      throw throwable;
    }

    NettyUtils.setServerSpanHttpStatus(span, response.status().code());
    span.finish(); // Finish the span manually since finishSpanOnClose was false
  }
}
