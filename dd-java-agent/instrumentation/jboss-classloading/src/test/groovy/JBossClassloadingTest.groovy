// Modified by SignalFx
import datadog.trace.agent.test.AgentTestRunner

class JBossClassloadingTest extends AgentTestRunner {
  def "delegation property set on module load"() {
    setup:
    org.jboss.modules.Module.getName()

    expect:
    System.getProperty("jboss.modules.system.pkgs") == "io.opentracing,datadog.slf4j,datadog.trace.bootstrap,datadog.trace.api,com.signalfx.tracing.api,datadog.trace.context,com.signalfx.tracing.context"
  }
}
