package org.apache.camel.opentracing.decorators;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.camel.opentracing.SpanDecorator;

public class DecoratorRegistry {

  private static final SpanDecorator DEFAULT = new DefaultSpanDecorator();
  private static final Map<String, SpanDecorator> DECORATORS = loadDecorators();

  private static Map<String, SpanDecorator> loadDecorators() {
    return Stream.of(
            new AhcSpanDecorator(),
            new AmqpSpanDecorator(),
            new AwsSnsSpanDecorator(),
            new AwsSqsSpanDecorator(),
            new CometdSpanDecorator(),
            new CometdsSpanDecorator(),
            new CqlSpanDecorator(),
            new DirectSpanDecorator(),
            new DirectvmSpanDecorator(),
            new DisruptorSpanDecorator(),
            new DisruptorvmSpanDecorator(),
            new ElasticsearchSpanDecorator(),
            new Http4SpanDecorator(),
            new HttpSpanDecorator(),
            new IronmqSpanDecorator(),
            new JdbcSpanDecorator(),
            new JettySpanDecorator(),
            new JmsSpanDecorator(),
            new KafkaSpanDecorator(),
            new LogSpanDecorator(),
            new MongoDBSpanDecorator(),
            new MqttSpanDecorator(),
            new NettyHttp4SpanDecorator(),
            new NettyHttpSpanDecorator(),
            new PahoSpanDecorator(),
            new RabbitmqSpanDecorator(),
            new RestletSpanDecorator(),
            new RestSpanDecorator(),
            new SedaSpanDecorator(),
            new ServletSpanDecorator(),
            new SjmsSpanDecorator(),
            new SqlSpanDecorator(),
            new StompSpanDecorator(),
            new TimerSpanDecorator(),
            new UndertowSpanDecorator(),
            new VmSpanDecorator())
        .collect(Collectors.toMap(SpanDecorator::getComponent, Function.identity()));
  }

  public SpanDecorator forComponent(final String component) {

    return DECORATORS.getOrDefault(component, DEFAULT);
  }
}
