package datadog.opentracing.mock.composite;

import datadog.opentracing.scopemanager.ContextualScopeManager;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.noop.NoopSpan;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ListIterator;

public class CompositeScopeManager extends ContextualScopeManager {
  protected Tracer[] childTracers;

  public CompositeScopeManager(Tracer[] tracers) {
    childTracers = tracers;
  }

  @Override
  // span should always be a CompositeSpan
  public Scope activate(Span span, boolean b) {
    List<Scope> scopes = new ArrayList<>();
    if (span instanceof NoopSpan) {
      for (Tracer tracer : childTracers) {
        scopes.add(tracer.scopeManager().activate(span, b));
      }
    } else {
      List<Span> childSpans = Arrays.asList(((CompositeSpan) span).childSpans);
      ListIterator iter = childSpans.listIterator();
      while (iter.hasNext()) {
        scopes.add(childTracers[iter.nextIndex()].scopeManager().activate((Span) iter.next(), b));
      }
    }
    return new CompositeScope(scopes.toArray(new Scope[0]));
  }

  @Override
  public Scope active() {
    List<Scope> scopes = new ArrayList<>();
    for (Tracer tracer : childTracers) {
      Scope scope = tracer.scopeManager().active();
      if (scope != null) {
        scopes.add(scope);
      }
    }
    if (scopes.size() == 0) {
      return null;
    }
    return new CompositeScope(scopes.toArray(new Scope[0]));
  }
}
