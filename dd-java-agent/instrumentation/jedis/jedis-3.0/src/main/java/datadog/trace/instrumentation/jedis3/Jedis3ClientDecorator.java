// Modified by SignalFx
package datadog.trace.instrumentation.jedis3;

import datadog.trace.instrumentation.jedis.JedisClientDecorator;
import redis.clients.jedis.commands.ProtocolCommand;

public class Jedis3ClientDecorator extends JedisClientDecorator<ProtocolCommand> {
  public static final Jedis3ClientDecorator DECORATE = new Jedis3ClientDecorator();

  @Override
  protected String dbUser(final ProtocolCommand session) {
    return null;
  }

  @Override
  protected String dbInstance(final ProtocolCommand session) {
    return null;
  }
}
