// Modified by SignalFx
package datadog.trace.instrumentation.vertx;

import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.propagate;

import datadog.trace.context.TraceScope;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AsyncResultHandlerConsumerWrapper implements Consumer<Handler<AsyncResult>> {

  private final Consumer<Handler<AsyncResult>> delegate;
  private final TraceScope.Continuation continuation;

  public AsyncResultHandlerConsumerWrapper(final Consumer<Handler<AsyncResult>> delegate) {
    this.delegate = delegate;
    continuation = propagate().capture();
  }

  @Override
  public void accept(final Handler<AsyncResult> asyncResultHandler) {
    if (continuation != null) {
      try (final TraceScope scope = continuation.activate()) {
        scope.setAsyncPropagation(true);
        delegate.accept(asyncResultHandler);
      }
    } else {
      delegate.accept(asyncResultHandler);
    }
  }

  public static Consumer<Handler<AsyncResult>> wrapIfNeeded(
      final Consumer<Handler<AsyncResult>> delegate) {
    if (!(delegate instanceof AsyncResultHandlerConsumerWrapper)) {
      log.debug("Wrapping consumer {}", delegate);
      return new AsyncResultHandlerConsumerWrapper(delegate);
    }
    return delegate;
  }
}
