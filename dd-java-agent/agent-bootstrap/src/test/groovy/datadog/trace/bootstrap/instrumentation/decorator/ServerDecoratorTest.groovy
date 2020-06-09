// Modified by SignalFx
package datadog.trace.bootstrap.instrumentation.decorator

import datadog.trace.api.DDTags
import datadog.trace.bootstrap.instrumentation.api.AgentSpan
import datadog.trace.bootstrap.instrumentation.api.Tags

class ServerDecoratorTest extends BaseDecoratorTest {

  def span = Mock(AgentSpan)

  def "test afterStart for a #spanType span"() {
    def decorator = newDecorator()
    when:
    decorator.afterStart(span)

    then:
    1 * span.setTag(Tags.COMPONENT, "test-component")
    1 * span.setTag(DDTags.SPAN_TYPE, decorator.spanType())
    if (decorator.traceAnalyticsEnabled) {
      1 * span.setTag(DDTags.ANALYTICS_SAMPLE_RATE, 1.0)
    }
    if (isRoot) {
      1 * span.getLocalRootSpan() >> span
      1 * span.setTag(Tags.SPAN_KIND, "server")
    } else {
      1 * span.getLocalRootSpan() >> Mock(AgentSpan)
    }
    0 * _

    where:
    spanType | isRoot
    "leaf"   | false
    "root"   | true
  }

  def "test afterStart set span kind"() {
    setup:
    def decorator = newDecorator()
    when:
    decorator.afterStart(span, setKind)

    then:
    1 * span.setTag(Tags.COMPONENT, "test-component")
    value * span.setTag(Tags.SPAN_KIND, "server")
    1 * span.setTag(DDTags.SPAN_TYPE, decorator.spanType())
    if (decorator.traceAnalyticsEnabled) {
      1 * span.setTag(DDTags.ANALYTICS_SAMPLE_RATE, 1.0)
    }
    value * span.getLocalRootSpan() >> span
    0 * _

    where:
    setKind     |   value
      true      |    1
      false     |    0
  }

  def "test beforeFinish"() {
    when:
    newDecorator().beforeFinish(span)

    then:
    0 * _
  }

  @Override
  def newDecorator() {
    return new ServerDecorator() {
      @Override
      protected String[] instrumentationNames() {
        return ["test1", "test2"]
      }

      @Override
      protected String spanType() {
        return "test-type"
      }

      @Override
      protected String component() {
        return "test-component"
      }

      protected boolean traceAnalyticsDefault() {
        return true
      }
    }
  }
}
