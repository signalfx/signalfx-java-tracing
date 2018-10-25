// Modified by SignalFx
import io.opentracing.mock.MockSpan
import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.api.CorrelationIdentifier
import io.opentracing.Scope
import io.opentracing.util.GlobalTracer

class TraceCorrelationTest extends AgentTestRunner {

  def "access trace correlation only under trace"() {
    when:
    Scope scope = GlobalTracer.get().buildSpan("myspan").startActive(true)
    MockSpan span = scope.span()

    then:
    CorrelationIdentifier.traceId == span.context().traceId()
    CorrelationIdentifier.spanId == span.context().spanId()

    when:
    scope.close()

    then:
    CorrelationIdentifier.traceId == 0
    CorrelationIdentifier.spanId == 0
  }
}
