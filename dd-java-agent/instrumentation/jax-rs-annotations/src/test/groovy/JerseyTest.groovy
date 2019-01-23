// Modified by SignalFx
import datadog.trace.agent.test.AgentTestRunner
import io.dropwizard.testing.junit.ResourceTestRule
import org.junit.ClassRule
import spock.lang.Shared
import io.opentracing.tag.Tags

import static datadog.trace.agent.test.TestUtils.runUnderTrace

class JerseyTest extends AgentTestRunner {

  @Shared
  @ClassRule
  ResourceTestRule resources = ResourceTestRule.builder().addResource(new TestResource()).build()

  def "test resource"() {
    setup:
    // start a trace because the test doesn't go through any servlet or other instrumentation.
    def response = runUnderTrace("test.span") {
      resources.client().resource("/test/hello/bob").post(String)
    }

    expect:
    response == "Hello bob!"
    TEST_WRITER.waitForTraces(1)
    TEST_WRITER.size() == 1

    def trace = TEST_WRITER.firstTrace()
    def span = trace[0]
    span.operationName == "POST /test/hello/{name}"
    span.tags["component"] == "jax-rs"
    span.tags["$Tags.SPAN_KIND.key"] == "$Tags.SPAN_KIND_SERVER"
    span.tags["$Tags.COMPONENT.key"] == "jax-rs"
    span.tags["$Tags.HTTP_URL.key"] == "/test/hello/{name}"
    span.tags["$Tags.HTTP_METHOD.key"] == "POST"
  }
}
