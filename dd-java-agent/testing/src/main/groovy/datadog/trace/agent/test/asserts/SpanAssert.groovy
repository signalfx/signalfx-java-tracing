// Modified by SignalFx
package datadog.trace.agent.test.asserts

import datadog.opentracing.mock.TestSpan
import datadog.trace.api.DDTags
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType

import static TagsAssert.assertTags

class SpanAssert {
  private final TestSpan span

  private SpanAssert(span) {
    this.span = span
  }

  static void assertSpan(TestSpan span,
                         @ClosureParams(value = SimpleType, options = ['datadog.trace.agent.test.asserts.SpanAssert'])
                         @DelegatesTo(value = SpanAssert, strategy = Closure.DELEGATE_FIRST) Closure spec) {
    def asserter = new SpanAssert(span)
    def clone = (Closure) spec.clone()
    clone.delegate = asserter
    clone.resolveStrategy = Closure.DELEGATE_FIRST
    clone(asserter)
  }

  def assertSpanNameContains(String spanName, String... shouldContainArr) {
    if (spanName == null) {
      return false
    }
    for (String shouldContain : shouldContainArr) {
      assert spanName.contains(shouldContain)
    }
  }

  def serviceName(String name) {
    assert span.getServiceName() == name
  }

  def operationName(String name) {
    assert span.getOperationName() == name
  }

  def operationNameContains(String... operationNameParts) {
    assertSpanNameContains(span.operationName, operationNameParts)
  }

  def resourceName(String name) {
    assert span.tags.get(DDTags.RESOURCE_NAME) == name
  }

  def resourceNameContains(String... resourceNameParts) {
    assertSpanNameContains(span.tags.get(DDTags.RESOURCE_NAME), resourceNameParts)
  }

  def parent() {
    assert span.parentId() == 0
  }

  def parentId(long parentId) {
    assert span.parentId() == parentId
  }

  def traceId(long traceId) {
    assert span.context().traceId() == traceId
  }

  def childOf(TestSpan parent) {
    assert span.parentId() == parent.context().spanId()
    assert span.context().traceId() == parent.context().traceId()
  }

  def errored(boolean errored) {
    assert span.tags().get("error", false) == errored
  }

  void tags(@ClosureParams(value = SimpleType, options = ['datadog.trace.agent.test.asserts.TagsAssert'])
            @DelegatesTo(value = TagsAssert, strategy = Closure.DELEGATE_FIRST) Closure spec) {
    assertTags(span, spec)
  }
}
