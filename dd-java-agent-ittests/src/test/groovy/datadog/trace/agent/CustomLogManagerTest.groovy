package datadog.trace.agent

import datadog.trace.agent.test.IntegrationTestUtils
import jvmbootstraptest.LogManagerSetter
import spock.lang.Specification

import java.lang.management.ManagementFactory
import java.lang.management.RuntimeMXBean

class CustomLogManagerTest extends Specification {
  /**
   * Forked jvm test must run in a pure java runtime because groovy sets the global log manager.
   */
  def "javaagent setup does not set the global log manager"() {
    setup:
    final RuntimeMXBean runtimeMxBean = ManagementFactory.getRuntimeMXBean()
    String agentArg = null
    for (String arg : runtimeMxBean.getInputArguments()) {
      if (arg.startsWith("-javaagent")) {
        agentArg = arg
        break
      }
    }
    final URL customAgent = IntegrationTestUtils.createJarWithClasses(LogManagerSetter.getName(), LogManagerSetter)

    expect:
    agentArg != null
    IntegrationTestUtils.runOnSeparateJvm(LogManagerSetter.getName()
      , [agentArg, "-javaagent:" + customAgent.getPath(), "-Dsfx.jmxfetch.enabled=true"] as String[]
      , "" as String[]
      , true) == 0
  }
}
