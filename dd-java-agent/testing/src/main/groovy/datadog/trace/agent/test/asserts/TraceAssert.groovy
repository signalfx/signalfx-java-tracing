// Modified by SignalFx 
package datadog.trace.agent.test.asserts

import datadog.opentracing.DDSpan
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import io.opentracing.tag.Tags

import static SpanAssert.assertSpan

class TraceAssert {
  private final List<DDSpan> trace
  private final int size
  private final Set<Integer> assertedIndexes = new HashSet<>()

  private TraceAssert(trace) {
    this.trace = trace
    size = trace.size()
  }

  static void assertTrace(List<DDSpan> trace, int expectedSize,
                          @ClosureParams(value = SimpleType, options = ['datadog.trace.agent.test.asserts.TraceAssert'])
                          @DelegatesTo(value = TraceAssert, strategy = Closure.DELEGATE_FIRST) Closure spec) {
    assert trace.size() == expectedSize
    def asserter = new TraceAssert(trace)
    def clone = (Closure) spec.clone()
    clone.delegate = asserter
    clone.resolveStrategy = Closure.DELEGATE_FIRST
    clone(asserter)
    asserter.assertSpansAllVerified()
    asserter.assertNoMoreThanOneServerSpan()
  }

  DDSpan span(int index) {
    trace.get(index)
  }

  DDSpan spanByOperationName(String operationName) {
    span(spanIndexByOperationName(operationName))
  }

  void span(int index, @ClosureParams(value = SimpleType, options = ['datadog.trace.agent.test.asserts.SpanAssert']) @DelegatesTo(value = SpanAssert, strategy = Closure.DELEGATE_FIRST) Closure spec) {
    if (index >= size) {
      throw new ArrayIndexOutOfBoundsException(index)
    }
    if (trace.size() != size) {
      throw new ConcurrentModificationException("Trace modified during assertion")
    }
    assertedIndexes.add(index)
    assertSpan(trace.get(index), spec)
  }

  void spanByOperationName(String operationName, @ClosureParams(value = SimpleType, options = ['datadog.trace.agent.test.asserts.SpanAssert']) @DelegatesTo(value = SpanAssert, strategy = Closure.DELEGATE_FIRST) Closure spec) {
    span(spanIndexByOperationName(operationName), spec)
  }

  void assertSpansAllVerified() {
    assert assertedIndexes.size() == size
  }

  void assertNoMoreThanOneServerSpan() {
    def serverSpans = new ArrayList<DDSpan>()
    for (DDSpan span : trace) {
      def tags = span.getTags()
      if (tags != null && Tags.SPAN_KIND_SERVER == tags.get(Tags.SPAN_KIND.getKey())) {
        serverSpans.add(span)
      }
    }
    if (serverSpans.size() > 1) {
      throw new IllegalStateException("More than one server span found, server spans: " + serverSpans)
    }
  }

  private int spanIndexByOperationName(String operationName) {
    trace.findIndexOf { it.operationName == operationName }
  }
}
