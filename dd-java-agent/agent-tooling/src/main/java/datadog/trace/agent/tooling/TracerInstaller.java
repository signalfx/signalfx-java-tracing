/** Modified by SignalFx to use Jaeger Tracer instead of DDTracer */
package datadog.trace.agent.tooling;

import com.google.common.base.Strings;
import datadog.opentracing.scopemanager.ContextualScopeManager;
import datadog.trace.api.Config;
import io.jaegertracing.Configuration;
import io.opentracing.Tracer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TracerInstaller {
  /** Register a global tracer if no global tracer is already registered. */
  public static synchronized void installGlobalTracer() {
    if (!io.opentracing.util.GlobalTracer.isRegistered()) {
      String serviceName = System.getenv("JAEGER_SERVICE_NAME");
      if (Strings.isNullOrEmpty(serviceName)) {
        serviceName = Config.get().getServiceName();
      }

      Configuration conf = Configuration.fromEnv(serviceName);

      Configuration.SenderConfiguration senderConf = conf.getReporter().getSenderConfiguration();
      if (Strings.isNullOrEmpty(senderConf.getAuthUsername())
          && !Strings.isNullOrEmpty(senderConf.getAuthPassword())) {
        // This builder updates in place
        senderConf.withAuthUsername("auth");
      }

      if (Strings.isNullOrEmpty(senderConf.getEndpoint())) {
        senderConf.withEndpoint(
            "http://"
                + Config.get().getAgentHost()
                + ":"
                + Config.get().getAgentPort()
                + Config.get().getAgentPath());
      }

      if (Strings.isNullOrEmpty(conf.getSampler().getType())) {
        // Send all traces out by default since sampling happens downstream
        conf =
            conf.withSampler(
                new Configuration.SamplerConfiguration().withType("const").withParam(1));
      }

      final Tracer tracer =
          conf.getTracerBuilder().withScopeManager(new ContextualScopeManager()).build();

      log.info(
          "Sending traces to "
              + senderConf.getEndpoint()
              + " with service name "
              + conf.getServiceName());

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
