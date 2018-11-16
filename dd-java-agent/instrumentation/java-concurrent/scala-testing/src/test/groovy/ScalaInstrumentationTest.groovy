// Modified by SignalFx
import datadog.opentracing.mock.TestSpan
import datadog.trace.agent.test.AgentTestRunner

class ScalaInstrumentationTest extends AgentTestRunner {

  def "scala futures and callbacks"() {
    setup:
    ScalaConcurrentTests scalaTest = new ScalaConcurrentTests()
    int expectedNumberOfSpans = scalaTest.traceWithFutureAndCallbacks()
    TEST_WRITER.waitForTraces(1)
    List<TestSpan> trace = TEST_WRITER.get(0)

    expect:
    trace.size() == expectedNumberOfSpans
    trace[0].operationName == "ScalaConcurrentTests.traceWithFutureAndCallbacks"
    findSpan(trace, "goodFuture").parentId == trace[0].spanId
    findSpan(trace, "badFuture").parentId == trace[0].spanId
    findSpan(trace, "successCallback").parentId == trace[0].spanId
    findSpan(trace, "failureCallback").parentId == trace[0].spanId
  }

  def "scala propagates across futures with no traces"() {
    setup:
    ScalaConcurrentTests scalaTest = new ScalaConcurrentTests()
    int expectedNumberOfSpans = scalaTest.tracedAcrossThreadsWithNoTrace()
    TEST_WRITER.waitForTraces(1)
    List<TestSpan> trace = TEST_WRITER.get(0)

    expect:
    trace.size() == expectedNumberOfSpans
    trace[0].operationName == "ScalaConcurrentTests.tracedAcrossThreadsWithNoTrace"
    findSpan(trace, "callback").parentId == trace[0].spanId
  }

  def "scala either promise completion"() {
    setup:
    ScalaConcurrentTests scalaTest = new ScalaConcurrentTests()
    int expectedNumberOfSpans = scalaTest.traceWithPromises()
    TEST_WRITER.waitForTraces(1)
    List<TestSpan> trace = TEST_WRITER.get(0)

    expect:
    TEST_WRITER.size() == 1
    trace.size() == expectedNumberOfSpans
    trace[0].operationName == "ScalaConcurrentTests.traceWithPromises"
    findSpan(trace, "keptPromise").parentId == trace[0].spanId
    findSpan(trace, "keptPromise2").parentId == trace[0].spanId
    findSpan(trace, "brokenPromise").parentId == trace[0].spanId
  }

  def "scala first completed future"() {
    setup:
    ScalaConcurrentTests scalaTest = new ScalaConcurrentTests()
    int expectedNumberOfSpans = scalaTest.tracedWithFutureFirstCompletions()
    TEST_WRITER.waitForTraces(1)
    List<TestSpan> trace = TEST_WRITER.get(0)

    expect:
    TEST_WRITER.size() == 1
    trace.size() == expectedNumberOfSpans
    findSpan(trace, "timeout1").parentId == trace[0].spanId
    findSpan(trace, "timeout2").parentId == trace[0].spanId
    findSpan(trace, "timeout3").parentId == trace[0].spanId
  }

  private TestSpan findSpan(List<TestSpan> trace, String opName) {
    for (TestSpan span : trace) {
      if (span.getOperationName() == opName) {
        return span
      }
    }
    return null
  }
}
