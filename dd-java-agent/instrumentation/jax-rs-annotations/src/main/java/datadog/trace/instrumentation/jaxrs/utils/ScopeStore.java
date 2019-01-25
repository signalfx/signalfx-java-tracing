package datadog.trace.instrumentation.jaxrs.utils;

import io.opentracing.Scope;

/**
 * A mechanism to store a Scope and flag for whether it should be closed upon stopSpan cleanup for
 * controller and parent spans created by jax-rs-annotation instrumentation.
 */
public class ScopeStore {
  public final Scope scope;
  public final boolean close;

  public ScopeStore(final Scope scope, final boolean close) {
    this.scope = scope;
    this.close = close;
  }
}
