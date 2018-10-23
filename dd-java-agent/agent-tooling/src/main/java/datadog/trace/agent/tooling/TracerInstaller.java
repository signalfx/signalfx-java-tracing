/** Modified by SignalFx to use Jaeger Tracer instead of DDTracer */
package datadog.trace.agent.tooling;

import io.jaegertracing.Configuration;
import io.opentracing.Tracer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TracerInstaller {
  /** Register a global tracer if no global tracer is already registered. */
  public static synchronized void installGlobalTracer() {
    if (!io.opentracing.util.GlobalTracer.isRegistered()) {
      final Tracer tracer = Configuration.fromEnv().getTracer();
      try {
        io.opentracing.util.GlobalTracer.register(tracer);
      } catch (final RuntimeException re) {
        log.warn("Failed to register tracer '" + tracer + "'", re);
      }
    } else {
      log.debug("GlobalTracer already registered.");
    }
  }

  public static void logVersionInfo() {
    VersionLogger.logAllVersions();
    log.debug(
        io.opentracing.util.GlobalTracer.class.getName()
            + " loaded on "
            + io.opentracing.util.GlobalTracer.class.getClassLoader());
    log.debug(
        AgentInstaller.class.getName() + " loaded on " + AgentInstaller.class.getClassLoader());
  }
}
