// Modified by SignalFx
package datadog.trace.common.writer;

import static datadog.trace.api.Config.DD_AGENT_API_TYPE;
import static datadog.trace.api.Config.ZIPKIN_V2_API_TYPE;
import static datadog.trace.common.writer.DDAgentWriter.DDAgentWriterBuilder;

import datadog.opentracing.DDSpan;
import datadog.trace.api.Config;
import datadog.trace.common.writer.ddagent.DDAgentApi;
import datadog.trace.common.writer.ddagent.Monitor;
import java.io.Closeable;
import java.util.List;
import java.util.Properties;
import lombok.extern.slf4j.Slf4j;

/** A writer is responsible to send collected spans to some place */
public interface Writer extends Closeable {

  /**
   * Write a trace represented by the entire list of all the finished spans
   *
   * @param trace the list of spans to write
   */
  void write(List<DDSpan> trace);

  /** Start the writer */
  void start();

  /**
   * Indicates to the writer that no future writing will come and it should terminates all
   * connections and tasks
   */
  @Override
  void close();

  /** Count that a trace was captured for stats, but without reporting it. */
  void incrementTraceCount();

  @Slf4j
  final class Builder {

    public static Writer forConfig(final Config config) {
      final Writer writer;

      if (config != null) {
        final String configuredType = config.getWriterType();
        if (Config.DD_AGENT_WRITER_TYPE.equals(configuredType)) {
          writer = createAgentWriter(config);
        } else if (Config.LOGGING_WRITER_TYPE.equals(configuredType)) {
          writer = new LoggingWriter();
        } else {
          log.warn(
              "Writer type not configured correctly: Type {} not recognized. Defaulting to DDAgentWriter.",
              configuredType);
          writer = createAgentWriter(config);
        }
      } else {
        log.warn(
            "Writer type not configured correctly: No config provided! Defaulting to DDAgentWriter.");
        writer = DDAgentWriter.builder().build();
      }

      return writer;
    }

    public static Writer forConfig(final Properties config) {
      return forConfig(Config.get(config));
    }

    private static Writer createAgentWriter(final Config config) {
      DDAgentWriterBuilder agentWriterBuilder = DDAgentWriter.builder();
      Api api;
      if (DD_AGENT_API_TYPE.equals(config.getApiType())) {
        api = createApi(config);
      } else if (ZIPKIN_V2_API_TYPE.equals(config.getApiType())) {
        api =
            new ZipkinV2Api(
                config.getAgentHost(),
                config.getAgentPort(),
                config.getAgentPath(),
                config.getAgentUseHTTPS());
      } else {
        throw new IllegalArgumentException("Unknown api type: " + config.getApiType());
      }
      return agentWriterBuilder.agentApi(api).monitor(createMonitor(config)).build();
    }

    private static DDAgentApi createApi(final Config config) {
      return new DDAgentApi(
          config.getAgentHost(), config.getAgentPort(), config.getAgentUnixDomainSocket());
    }

    private static Monitor createMonitor(final Config config) {
      if (!config.isHealthMetricsEnabled()) {
        return new Monitor.Noop();
      } else {
        String host = config.getHealthMetricsStatsdHost();
        if (host == null) {
          host = config.getJmxFetchStatsdHost();
        }
        if (host == null) {
          host = config.getAgentHost();
        }

        Integer port = config.getHealthMetricsStatsdPort();
        if (port == null) {
          port = config.getJmxFetchStatsdPort();
        }

        return new Monitor.StatsD(host, port);
      }
    }

    private Builder() {}
  }
}
