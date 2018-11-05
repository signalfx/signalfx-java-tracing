// Modified by SignalFx
package datadog.trace.instrumentation.kafka_clients;

import io.opentracing.Scope;
import io.opentracing.Tracer;
import io.opentracing.Tracer.SpanBuilder;
import java.util.Iterator;
import lombok.extern.slf4j.Slf4j;

public class TracingIterable<T> implements Iterable<T> {
  private final Iterable<T> delegateIterable;
  private final SpanBuilderDecorator<T> decorator;

  public TracingIterable(
      final Iterable<T> delegateIterable, final SpanBuilderDecorator<T> decorator) {
    this.delegateIterable = delegateIterable;
    this.decorator = decorator;
  }

  @Override
  public Iterator<T> iterator() {
    return new TracingIterator<>(delegateIterable.iterator(), decorator);
  }

  @Slf4j
  public static class TracingIterator<T> implements Iterator<T> {
    private final Iterator<T> delegateIterator;
    private final SpanBuilderDecorator<T> decorator;

    private Scope currentScope;

    public TracingIterator(
        final Iterator<T> delegateIterator, final SpanBuilderDecorator<T> decorator) {
      this.delegateIterator = delegateIterator;
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
    public T next() {
      if (currentScope != null) {
        // in case they didn't call hasNext()...
        currentScope.close();
        currentScope = null;
      }

      final T next = delegateIterator.next();

      try {
        if (next != null) {
          final Tracer.SpanBuilder spanBuilder = decorator.buildSpan(next);
          currentScope = spanBuilder.startActive(true);
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

  public interface SpanBuilderDecorator<T> {
    SpanBuilder buildSpan(T context);
  }
}
