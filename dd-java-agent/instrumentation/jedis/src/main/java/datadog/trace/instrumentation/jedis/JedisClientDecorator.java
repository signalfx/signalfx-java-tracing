// Modified by SignalFx
package datadog.trace.instrumentation.jedis;

import datadog.trace.agent.decorator.DatabaseClientDecorator;
import datadog.trace.api.Config;
import datadog.trace.api.DDSpanTypes;
import io.opentracing.Span;
import java.io.UnsupportedEncodingException;

public abstract class JedisClientDecorator<COMMAND> extends DatabaseClientDecorator<COMMAND> {

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

  public Span onStatement(final Span span, final String commandName, final byte[][] cmdArgs) {
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
