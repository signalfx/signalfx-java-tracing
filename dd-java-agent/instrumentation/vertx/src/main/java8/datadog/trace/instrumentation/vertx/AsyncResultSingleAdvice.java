// Modified by SignalFx
package datadog.trace.instrumentation.vertx;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import java.util.function.Consumer;
import net.bytebuddy.asm.Advice;

public class AsyncResultSingleAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void wrapConsumer(
      @Advice.Argument(value = 0, readOnly = false) Consumer<Handler<AsyncResult>> consumer) {

    consumer = AsyncResultHandlerConsumerWrapper.wrapIfNeeded(consumer);
  }
}
