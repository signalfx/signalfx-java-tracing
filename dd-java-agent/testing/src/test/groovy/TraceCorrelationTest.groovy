// Modified by SignalFx
import datadog.opentracing.DDSpan
import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.api.CorrelationIdentifier
import io.opentracing.Scope
import io.opentracing.util.GlobalTracer

class TraceCorrelationTest extends AgentTestRunner {

  def "access trace correlation only under trace"() {
    when:
    Scope scope = GlobalTracer.get().buildSpan("myspan").startActive(true)
    DDSpan span = (DDSpan) scope.span()

    then:
    CorrelationIdentifier.traceId == String.format("%016x", new BigInteger(span.traceId, 10))
    CorrelationIdentifier.spanId == String.format("%016x", new BigInteger(span.spanId, 10))

    when:
    scope.close()

    then:
    CorrelationIdentifier.traceId == "0000000000000000"
    CorrelationIdentifier.spanId == "0000000000000000"
  }
}
