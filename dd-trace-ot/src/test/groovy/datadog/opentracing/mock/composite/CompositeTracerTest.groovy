package datadog.opentracing.mock.composite

import datadog.opentracing.mock.TestTracer
import datadog.opentracing.mock.ListWriter
import datadog.opentracing.scopemanager.ContextualScopeManager
import io.opentracing.Tracer
import io.opentracing.mock.MockSpan
import io.opentracing.mock.MockTracer
import io.opentracing.propagation.Format
import io.opentracing.propagation.TextMapExtractAdapter
import io.opentracing.propagation.TextMapInjectAdapter
import spock.lang.Specification
import spock.lang.Subject


class CompositeTracerTest extends Specification {
  def writer = new ListWriter()
  def testTracer = new TestTracer(writer)
  def mockTracer = new MockTracer(new ContextualScopeManager(), TestTracer.CONFLICT_FREE_TEXT_MAP)
  Tracer[] testTracers = [testTracer, mockTracer]
  def compositeTracer = new CompositeTracer(testTracers)

  @Subject
  def testScopeManager = testTracer.scopeManager()
  def mockScopeManager = mockTracer.scopeManager()
  def compositeScopeManager = compositeTracer.scopeManager()

  def cleanup() {
    testScopeManager.tlsScope.remove()
    testTracer.reset()
    mockTracer.reset()
  }

  def setup() {
    assert checkCleanTracers()
  }
  def checkCleanTracers() {
    assert testTracer.activeSpan() == null
    assert mockTracer.activeSpan() == null
    assert compositeTracer.activeSpan() == null

    assert testScopeManager.active() == null
    assert mockScopeManager.active() == null
    assert compositeScopeManager.active() == null
    return true
  }

  def checkScopes(comp, test, mock) {
    comp instanceof CompositeScope
    !(test instanceof CompositeScope)
    !(mock instanceof CompositeScope)
    return true
  }

  def checkSpans(comp, test, mock) {
    assert comp instanceof CompositeSpan
    assert test instanceof MockSpan
    assert mock instanceof MockSpan
    assert test != mock
    return true
  }

  def "new composite spans are created in corresponding tracers"() {
    when:
    def compositeScope = compositeTracer.buildSpan("MyOperation").startActive(true)
    def testScope = testScopeManager.active()
    def mockScope = mockScopeManager.active()

    def compositeSpan = compositeScope.span()
    def testSpan = testScope.span()
    def mockSpan = mockScope.span()

    then:
    compositeScope instanceof CompositeScope
    !(testScope instanceof CompositeScope)
    !(mockScope instanceof CompositeScope)

    compositeSpan instanceof CompositeSpan
    testSpan instanceof MockSpan
    mockSpan instanceof MockSpan
    testSpan != mockSpan

    testTracer.activeSpan() == testSpan
    mockTracer.activeSpan() == mockSpan

    when:
    compositeScope.close()

    then:
    checkCleanTracers()
  }

  def "child spans within parent tracers"() {
    when:
    def parentCompositeScope = compositeTracer.buildSpan("Root").startActive(true)
    def parentTestScope = testScopeManager.active()
    def parentMockScope = mockScopeManager.active()
    def parentCompositeSpan = parentCompositeScope.span()
    def parentTestSpan = parentTestScope.span()
    def parentMockSpan = parentMockScope.span()

    then:
    checkScopes(parentCompositeScope, parentTestScope, parentMockScope)
    checkSpans(parentCompositeSpan, parentTestSpan, parentMockSpan)
    parentTestSpan.parentId() == 0
    parentMockSpan.parentId() == 0

    def f1CompositeScope = compositeTracer.buildSpan("F1").startActive(true)
    def f1TestScope = testScopeManager.active()
    def f1MockScope = mockScopeManager.active()
    def f1CompositeSpan = f1CompositeScope.span()
    def f1TestSpan = f1TestScope.span()
    def f1MockSpan = f1MockScope.span()

    then:
    checkScopes(f1CompositeScope, f1TestScope, f1MockScope)
    checkSpans(f1CompositeSpan, f1TestSpan, f1MockSpan)
    f1TestSpan.context().traceId() == parentTestSpan.context().traceId()
    f1TestSpan.parentId() == parentTestSpan.context().spanId()
    f1MockSpan.context().traceId() == parentMockSpan.context().traceId()
    f1MockSpan.parentId() == parentMockSpan.context().spanId()

    def f2CompositeScope = compositeTracer.buildSpan("F2").startActive(true)
    def f2TestScope = testScopeManager.active()
    def f2MockScope = mockScopeManager.active()
    def f2CompositeSpan = f2CompositeScope.span()
    def f2TestSpan = f2TestScope.span()
    def f2MockSpan = f2MockScope.span()

    then:
    checkScopes(f2CompositeScope, f2TestScope, f2MockScope)
    checkSpans(f2CompositeSpan, f2TestSpan, f2MockSpan)
    f2TestSpan.context().traceId() == parentTestSpan.context().traceId()
    f2TestSpan.parentId() == f1TestSpan.context().spanId()
    f1MockSpan.context().traceId() == parentMockSpan.context().traceId()
    f2MockSpan.parentId() == f1MockSpan.context().spanId()

    when:
    f2CompositeScope.close()
    f1CompositeScope.close()
    parentCompositeScope.close()

    then:
    checkCleanTracers()
  }

  def "inject and extract work with child tracers"() {
    when:
    def compositeScope = compositeTracer.buildSpan("Root").startActive(true)
    def compositeContext = compositeScope.span().context()
    def spanContexts = compositeContext.childSpanContexts
    def testContext = testTracer.activeSpan().context()
    def mockContext = mockTracer.activeSpan().context()

    then:
    compositeContext instanceof CompositeSpan.CompositeSpanContext
    spanContexts.size() == 2
    spanContexts[0] == testContext
    spanContexts[1] == mockContext
    testContext.traceId() != mockContext.traceId()
    testContext.spanId() != mockContext.spanId()

    when:
    Map<String, String> headers = new HashMap<>()
    compositeTracer.inject(compositeContext, Format.Builtin.HTTP_HEADERS, new TextMapInjectAdapter(headers))

    then:
    headers.get("traceid") == testContext.traceId().toString()
    headers.get("spanid") == testContext.spanId().toString()
    headers.get("cf-traceid") == mockContext.traceId().toString()
    headers.get("cf-spanid") == mockContext.spanId().toString()

    when:
    def extractedContext = compositeTracer.extract(Format.Builtin.HTTP_HEADERS, new TextMapExtractAdapter(headers))
    def extractedContexts = extractedContext.childSpanContexts

    then:
    extractedContexts[0].traceId() == testContext.traceId()
    extractedContexts[0].spanId() == testContext.spanId()
    extractedContexts[1].traceId() == mockContext.traceId()
    extractedContexts[1].spanId() == mockContext.spanId()
  }

  def "asChildOf works with CompositeSpanContext"() {
    when:
    def headers = ["traceid":"1", "spanid": "2", "cf-traceid": "3", "cf-spanid": "4"]
    def extractedContext = compositeTracer.extract(Format.Builtin.HTTP_HEADERS, new TextMapExtractAdapter(headers))
    def extractedContexts = extractedContext.childSpanContexts

    then:
    extractedContexts[0].traceId() == 1
    extractedContexts[0].spanId() == 2
    extractedContexts[1].traceId() == 3
    extractedContexts[1].spanId() == 4

    when:
    def childCompositeSpan = compositeTracer.buildSpan("Child").asChildOf(extractedContext).start()
    def childTestSpan = childCompositeSpan.childSpans[0]
    def childMockSpan = childCompositeSpan.childSpans[1]

    then:
    childTestSpan.context().traceId() == 1
    childTestSpan.parentId() == 2
    childMockSpan.context().traceId() == 3
    childMockSpan.parentId() == 4
  }

  def "asChildOf works with CompositeSpan"() {
    when:
    def parentCompositeSpan = compositeTracer.buildSpan("Root").start()
    def parentTestSpan = parentCompositeSpan.childSpans[0]
    def parentMockSpan = parentCompositeSpan.childSpans[1]

    def childCompositeSpan = compositeTracer.buildSpan("Child").asChildOf(parentCompositeSpan).start()
    def childTestSpan = childCompositeSpan.childSpans[0]
    def childMockSpan = childCompositeSpan.childSpans[1]

    then:
    childTestSpan.context().traceId() == parentTestSpan.context().traceId()
    childTestSpan.parentId() == parentTestSpan.context().spanId()
    childMockSpan.context().traceId() == parentMockSpan.context().traceId()
    childMockSpan.parentId() == parentMockSpan.context().spanId()
  }
}
