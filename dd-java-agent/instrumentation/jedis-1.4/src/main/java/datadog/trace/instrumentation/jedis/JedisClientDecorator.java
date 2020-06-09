// Modified by SignalFx
package datadog.trace.instrumentation.jedis;

import datadog.trace.api.Config;
import datadog.trace.api.DDSpanTypes;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.bootstrap.instrumentation.decorator.DatabaseClientDecorator;
import java.io.UnsupportedEncodingException;
import redis.clients.jedis.Protocol;

public class JedisClientDecorator extends DatabaseClientDecorator<Protocol.Command> {
  public static final JedisClientDecorator DECORATE = new JedisClientDecorator();

  @Override
  protected String[] instrumentationNames() {
    return new String[] {"jedis", "redis"};
  }

  @Override
  protected String service() {
    return "redis";
  }

  @Override
  protected String component() {
    return "redis";
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
  protected String dbUser(final Protocol.Command session) {
    return null;
  }

  @Override
  protected String dbInstance(final Protocol.Command session) {
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
