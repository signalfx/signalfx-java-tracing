// Modified by SignalFx
package datadog.trace.agent.tooling;

import java.lang.reflect.Method;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DDJavaAgentInfo {
  public static final String VERSION;

  static {
    String v;
    try {
      Class<?> tracingAgentClass =
          ClassLoader.getSystemClassLoader().loadClass("datadog.trace.agent.TracingAgent");
      Method getAgentVersionMethod = tracingAgentClass.getMethod("getAgentVersion");
      v = (String) getAgentVersionMethod.invoke(null);
    } catch (final Exception e) {
      log.error("failed to read agent version", e);
      v = "unknown";
    }
    VERSION = v;
    log.info("signalfx-java-agent - version: {}", v);
  }
}
