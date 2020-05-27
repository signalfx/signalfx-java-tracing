// Modified by SignalFx
package datadog.trace.instrumentation.jedis30;

import datadog.trace.api.Config;
import datadog.trace.api.DDSpanTypes;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.bootstrap.instrumentation.decorator.DatabaseClientDecorator;
import java.io.UnsupportedEncodingException;
import redis.clients.jedis.commands.ProtocolCommand;

public class JedisClientDecorator extends DatabaseClientDecorator<ProtocolCommand> {
  public static final JedisClientDecorator DECORATE = new JedisClientDecorator();

  private static final String SERVICE_NAME = "redis";
  private static final String COMPONENT_NAME = SERVICE_NAME;

  @Override
  protected String[] instrumentationNames() {
    return new String[] {"jedis", "redis"};
  }

  @Override
  protected String service() {
    return SERVICE_NAME;
  }

  @Override
  protected String component() {
    return COMPONENT_NAME;
  }

  @Override
  protected String spanType() {
    return DDSpanTypes.REDIS;
  }

  @Override
  protected String dbType() {
    return "redis";
  }

  @Override
  protected String dbUser(final ProtocolCommand session) {
    return null;
  }

  @Override
  protected String dbInstance(final ProtocolCommand session) {
    return null;
  }

  public AgentSpan onStatement(
      final AgentSpan span, final String commandName, final byte[][] cmdArgs) {
    String statement = commandName;
    if (cmdArgs.length > 0
        && !statement.toLowerCase().equals("auth")
        && Config.get().isRedisCaptureCommandArguments()) {
      statement += ":";
      for (final byte[] word : cmdArgs) {
        try {
          statement += " " + new String(word, "UTF-8");
        } catch (UnsupportedEncodingException e) {
        }
      }
    }
    return super.onStatement(span, statement);
  }
}
