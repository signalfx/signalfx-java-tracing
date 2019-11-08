// Modifed by SignalFx
package datadog.trace.instrumentation.jedis1;

import datadog.trace.instrumentation.jedis.JedisClientDecorator;
import redis.clients.jedis.Protocol;

public class Jedis1ClientDecorator extends JedisClientDecorator<Protocol.Command> {
  public static final Jedis1ClientDecorator DECORATE = new Jedis1ClientDecorator();

  @Override
  protected String dbUser(final Protocol.Command session) {
    return null;
  }

  @Override
  protected String dbInstance(final Protocol.Command session) {
    return null;
  }
}
