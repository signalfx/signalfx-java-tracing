package datadog.trace.instrumentation.kafka_clients;

import datadog.trace.api.Config;
import io.opentracing.Scope;
import io.opentracing.SpanContext;
import io.opentracing.propagation.Format;
import io.opentracing.util.GlobalTracer;
import java.util.Iterator;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;

public class TracingIterable implements Iterable<ConsumerRecord> {
  private final Iterable<ConsumerRecord> delegateIterable;
  private final String operationName;
  private final KafkaDecorator decorator;

  public TracingIterable(
      final Iterable<ConsumerRecord> delegateIterable,
      final String operationName,
      final KafkaDecorator decorator) {
    this.delegateIterable = delegateIterable;
    this.operationName = operationName;
    this.decorator = decorator;
  }

  @Override
  public Iterator<ConsumerRecord> iterator() {
    return new TracingIterator(delegateIterable.iterator(), operationName, decorator);
  }

  @Slf4j
  public static class TracingIterator implements Iterator<ConsumerRecord> {
    private final Iterator<ConsumerRecord> delegateIterator;
    private final String operationName;
    private final KafkaDecorator decorator;

    private Scope currentScope;

    public TracingIterator(
        final Iterator<ConsumerRecord> delegateIterator,
        final String operationName,
        final KafkaDecorator decorator) {
      this.delegateIterator = delegateIterator;
      this.operationName = operationName;
      this.decorator = decorator;
    }

    @Override
    public boolean hasNext() {
      if (currentScope != null) {
        currentScope.close();
        currentScope = null;
      }
      return delegateIterator.hasNext();
    }

    @Override
    public ConsumerRecord next() {
      if (currentScope != null) {
        // in case they didn't call hasNext()...
        currentScope.close();
        currentScope = null;
      }

      final ConsumerRecord next = delegateIterator.next();

      try {
        if (next != null) {
          SpanContext spanContext = null;
          if (Config.get().isKafkaAttemptPropagation()) {
            spanContext =
                GlobalTracer.get()
                    .extract(Format.Builtin.TEXT_MAP, new TextMapExtractAdapter(next.headers()));
          }
          currentScope =
              GlobalTracer.get().buildSpan(operationName).asChildOf(spanContext).startActive(true);
          decorator.afterStart(currentScope);
          decorator.onConsume(currentScope, next);
        }
      } catch (final Exception e) {
        log.debug("Error during decoration", e);
      }
      return next;
    }

    @Override
    public void remove() {
      delegateIterator.remove();
    }
  }
}
