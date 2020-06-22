package datadog.trace.instrumentation.netty38.server;

import static datadog.trace.instrumentation.netty38.server.NettyHttpServerDecorator.DECORATE;

import datadog.trace.api.Config;
import datadog.trace.bootstrap.ContextStore;
import datadog.trace.bootstrap.instrumentation.TraceParentHeaderFormatter;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.bootstrap.instrumentation.api.Tags;
import datadog.trace.instrumentation.netty38.ChannelTraceContext;
import io.opentracing.SpanContext;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelDownstreamHandler;
import org.jboss.netty.handler.codec.http.HttpResponse;

public class HttpServerResponseTracingHandler extends SimpleChannelDownstreamHandler {

  private final ContextStore<Channel, ChannelTraceContext> contextStore;

  public HttpServerResponseTracingHandler(
      final ContextStore<Channel, ChannelTraceContext> contextStore) {
    this.contextStore = contextStore;
  }

  @Override
  public void writeRequested(final ChannelHandlerContext ctx, final MessageEvent msg)
      throws Exception {
    final ChannelTraceContext channelTraceContext =
        contextStore.putIfAbsent(ctx.getChannel(), ChannelTraceContext.Factory.INSTANCE);

    final AgentSpan span = channelTraceContext.getServerSpan();
    if (span == null || !(msg.getMessage() instanceof HttpResponse)) {
      ctx.sendDownstream(msg);
      return;
    }

    final HttpResponse response = (HttpResponse) msg.getMessage();
    if (Config.get().isEmitServerTimingContext() && response != null) {
      response
          .headers()
          .add("Server-Timing", TraceParentHeaderFormatter.format((SpanContext) span.context()));
      response.headers().add("Access-Control-Expose-Headers", "Server-Timing");
    }

    try {
      ctx.sendDownstream(msg);
    } catch (final Throwable throwable) {
      DECORATE.onError(span, throwable);
      span.setTag(Tags.HTTP_STATUS, 500);
      span.finish(); // Finish the span manually since finishSpanOnClose was false
      throw throwable;
    }
    DECORATE.onResponse(span, response);
    DECORATE.beforeFinish(span);
    span.finish(); // Finish the span manually since finishSpanOnClose was false
  }
}
