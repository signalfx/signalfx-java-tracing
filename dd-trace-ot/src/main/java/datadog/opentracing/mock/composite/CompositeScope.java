package datadog.opentracing.mock.composite;

import datadog.trace.context.TraceScope;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.noop.NoopSpan;
import java.util.ArrayList;
import java.util.List;

public class CompositeScope implements Scope, TraceScope {
  protected Scope[] childScopes;

  public CompositeScope(Scope[] scopes) {
    childScopes = scopes;
  }

  @Override
  public void close() {
    for (Scope scope : childScopes) {
      scope.close();
    }
  }

  @Override
  public Span span() {
    boolean noopSpans = false;
    List<Span> spans = new ArrayList<>();
    for (Scope scope : childScopes) {
      Span span = scope.span();
      if (span instanceof NoopSpan) {
        noopSpans = true;
      }
      spans.add(span);
    }
    if (noopSpans) {
      return new CompositeNoopSpan(spans.toArray(new Span[0]));
    }
    return new CompositeSpan(spans.toArray(new Span[0]));
  }

  @Override
  public Continuation capture() {
    return ((TraceScope) childScopes[0]).capture();
  }

  @Override
  public boolean isAsyncPropagating() {
    return ((TraceScope) childScopes[0]).isAsyncPropagating();
  }

  @Override
  public void setAsyncPropagation(boolean value) {
    for (Scope scope : childScopes) {
      System.out.println("CompositeScope.setAsyncPropagation: " + scope.toString());
      ((TraceScope) scope).setAsyncPropagation(value);
    }
  }

  private class CompositeNoopSpan extends CompositeSpan implements NoopSpan {
    public CompositeNoopSpan(Span[] spans) {
      super(spans);
    }
  }
}
