// Modified by SignalFx
package datadog.opentracing.scopemanager

import datadog.trace.agent.test.utils.TestTracer
import datadog.trace.agent.test.utils.ListWriter
import io.opentracing.Scope
import io.opentracing.Span
import io.opentracing.noop.NoopSpan
import spock.lang.Specification
import spock.lang.Subject

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class ScopeManagerTest extends Specification {
  def writer = new ListWriter()
  def tracer = new TestTracer(writer)

  @Subject
  def scopeManager = tracer.scopeManager()

  def cleanup() {
    scopeManager.tlsScope.remove()
  }

  def "non-ddspan activation results in a continuable scope"() {
    when:
    def scope = scopeManager.activate(NoopSpan.INSTANCE, true)

    then:
    scopeManager.active() == scope
    scope instanceof ContinuableScope

    when:
    scope.close()

    then:
    scopeManager.active() == null
  }

  def "threadlocal is empty"() {
    setup:
    def builder = tracer.buildSpan("test")
    builder.start()

    expect:
    scopeManager.active() == null
    writer.empty
  }

  def "threadlocal is active"() {
    when:
    def builder = tracer.buildSpan("test")
    def scope = builder.startActive(finishSpan)

    then:
    !spanFinished(scope.span())
    scopeManager.active() == scope
    scope instanceof ContinuableScope
    writer.empty

    when:
    scope.close()

    then:
    spanFinished(scope.span()) == finishSpan
    writer.collect{it.collect{it.span}} == [[scope.span()]] || !finishSpan
    scopeManager.active() == null

    where:
    finishSpan << [true, false]
  }

  def "sets parent as current upon close"() {
    setup:
    def parentScope = tracer.buildSpan("parent").startActive(finishSpan)
    def childScope = tracer.buildSpan("parent").startActive(finishSpan)

    expect:
    scopeManager.active() == childScope
    childScope.span().parentId() == parentScope.span().context().spanId()

    when:
    childScope.close()

    then:
    scopeManager.active() == parentScope
    spanFinished(childScope.span()) == finishSpan
    !spanFinished(parentScope.span())
    writer == []

    when:
    parentScope.close()

    then:
    spanFinished(childScope.span()) == finishSpan
    spanFinished(parentScope.span()) == finishSpan
    writer.collect{it.collect{it.span}} == [[parentScope.span(), childScope.span()]] || !finishSpan
    scopeManager.active() == null

    where:
    finishSpan << [true, false]
  }

  def "ContinuableScope only creates continuations when propagation is set"() {
    setup:
    def builder = tracer.buildSpan("test")
    def scope = (ContinuableScope) builder.startActive(true)
    def continuation = scope.capture()

    expect:
    continuation == null

    when:
    scope.setAsyncPropagation(true)
    continuation = scope.capture()
    then:
    continuation != null

    cleanup:
    continuation.close()
  }

  def "ContinuableScope doesn't close if non-zero"() {
    setup:
    def builder = tracer.buildSpan("test")
    def scope = (ContinuableScope) builder.startActive(true)
    scope.setAsyncPropagation(true)
    def continuation = scope.capture()

    expect:
    !spanFinished(scope.span())
    scopeManager.active() == scope
    scope instanceof ContinuableScope
    writer.empty

    when:
    scope.close()

    then:
    !spanFinished(scope.span())
    scopeManager.active() == null
    writer.empty

    when:
    continuation.activate()
    if (forceGC) {
      continuation = null // Continuation references also hold up traces.
    }
    if (autoClose) {
      if (continuation != null) {
        continuation.close()
      }
    }

    then:
    scopeManager.active() != null

    when:
    scopeManager.active().close()
    writer.waitForTraces(1)

    then:
    scopeManager.active() == null
    spanFinished(scope.span())
    writer.collect{it.collect({it.span})} == [[scope.span()]]

    where:
    autoClose | forceGC
    true      | true
    true      | false
    false     | true
  }

  def "continuation restores trace"() {
    setup:
    def parentScope = tracer.buildSpan("parent").startActive(true)
    def parentSpan = parentScope.span()
    ContinuableScope childScope = (ContinuableScope) tracer.buildSpan("parent").startActive(true)
    childScope.setAsyncPropagation(true)
    def childSpan = childScope.span()

    def continuation = childScope.capture()
    childScope.close()

    expect:
    scopeManager.active() == parentScope
    !spanFinished(childSpan)
    !spanFinished(parentSpan)

    when:
    parentScope.close()

    then:
    scopeManager.active() == null
    !spanFinished(childSpan)
    spanFinished(parentSpan)

    when:
    def newScope = continuation.activate()
    newScope.setAsyncPropagation(true)
    def newContinuation = newScope.capture()

    then:
    newScope instanceof ContinuableScope
    scopeManager.active() == newScope
    newScope != childScope && newScope != parentScope
    newScope.span() == childSpan
    !spanFinished(childSpan)
    spanFinished(parentSpan)

    when:
    newScope.close()
    newContinuation.activate().close()

    then:
    scopeManager.active() == null
    spanFinished(childSpan)
    spanFinished(parentSpan)
    writer.collect{it.collect{it.span}} == [[childSpan, parentSpan]]
  }

  def "continuation allows adding spans even after other spans were completed"() {
    setup:
    def builder = tracer.buildSpan("test")
    def scope = (ContinuableScope) builder.startActive(false)
    def span = scope.span()
    scope.setAsyncPropagation(true)
    def continuation = scope.capture()
    scope.close()
    span.finish()

    def newScope = continuation.activate()

    expect:
    newScope instanceof ContinuableScope
    newScope != scope
    scopeManager.active() == newScope
    spanFinished(span)

    when:
    def childScope = tracer.buildSpan("child").startActive(true)
    def childSpan = childScope.span()
    childScope.close()
    scopeManager.active().close()

    then:
    scopeManager.active() == null
    spanFinished(childSpan)
    childSpan.parentId() == span.context().spanId()
  }

  def "context takes control (#active)"() {
    setup:
    contexts.each {
      scopeManager.addScopeContext(it)
    }
    def builder = tracer.buildSpan("test")
    def scope = (AtomicReferenceScope) builder.startActive(true)

    expect:
    scopeManager.tlsScope.get() == null
    scopeManager.active() == scope
    contexts[active].get() == scope.get()
    writer.empty

    where:
    active | contexts
    0      | [new AtomicReferenceScope(true)]
    1      | [new AtomicReferenceScope(true), new AtomicReferenceScope(true)]
    3      | [new AtomicReferenceScope(false), new AtomicReferenceScope(true), new AtomicReferenceScope(false), new AtomicReferenceScope(true)]
  }

  def "disabled context is ignored (#contexts.size)"() {
    setup:
    contexts.each {
      scopeManager.addScopeContext(it)
    }
    def builder = tracer.buildSpan("test")
    def scope = builder.startActive(true)

    expect:
    contexts.findAll {
      it.get() != null
    } == []

    scopeManager.tlsScope.get() == scope
    scopeManager.active() == scope
    writer.empty

    where:
    contexts                                                                                            | _
    []                                                                                                  | _
    [new AtomicReferenceScope(false)]                                                                   | _
    [new AtomicReferenceScope(false), new AtomicReferenceScope(false)]                                  | _
    [new AtomicReferenceScope(false), new AtomicReferenceScope(false), new AtomicReferenceScope(false)] | _
  }

  def "ContinuableScope put in threadLocal after continuation activation"() {
    setup:
    ContinuableScope scope = (ContinuableScope) tracer.buildSpan("parent").startActive(true)
    scope.setAsyncPropagation(true)

    expect:
    scopeManager.tlsScope.get() == scope

    when:
    def cont = scope.capture()
    scope.close()

    then:
    scopeManager.tlsScope.get() == null

    when:
    scopeManager.addScopeContext(new AtomicReferenceScope(true))
    def newScope = cont.activate()

    then:
    newScope != scope
    scopeManager.tlsScope.get() == newScope
  }

  def "context to threadlocal (#contexts.size)"() {
    setup:
    contexts.each {
      scopeManager.addScopeContext(it)
    }
    def scope = tracer.buildSpan("parent").startActive(false)
    def span = scope.span()

    expect:
    scope instanceof AtomicReferenceScope
    scopeManager.tlsScope.get() == null

    when:
    scope.close()
    contexts.each {
      ((AtomicBoolean) it.enabled).set(false)
    }
    scope = scopeManager.activate(span, true)

    then:
    scope instanceof ContinuableScope
    scopeManager.tlsScope.get() == scope

    where:
    contexts                                                         | _
    [new AtomicReferenceScope(true)]                                 | _
    [new AtomicReferenceScope(true), new AtomicReferenceScope(true)] | _
  }

  boolean spanFinished(Span span) {
    return tracer.finishedSpans().contains(span)
  }

  class AtomicReferenceScope extends AtomicReference<Span> implements ScopeContext, Scope {
    final AtomicBoolean enabled

    AtomicReferenceScope(boolean enabled) {
      this.enabled = new AtomicBoolean(enabled)
    }

    @Override
    boolean inContext() {
      return enabled.get()
    }

    @Override
    void close() {
      getAndSet(null).finish()
    }

    @Override
    Span span() {
      return get()
    }

    @Override
    Scope activate(Span span, boolean finishSpanOnClose) {
      set(span)
      return this
    }

    @Override
    Scope active() {
      return get() == null ? null : this
    }

    String toString() {
      return "Ref: " + super.toString()
    }
  }
}
