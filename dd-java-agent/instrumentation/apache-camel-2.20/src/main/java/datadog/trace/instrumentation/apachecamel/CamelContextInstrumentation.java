package datadog.trace.instrumentation.apachecamel;

import static datadog.trace.agent.tooling.ClassLoaderMatcher.hasClassesNamed;
import static datadog.trace.agent.tooling.bytebuddy.matcher.DDElementMatchers.implementsInterface;
import static net.bytebuddy.matcher.ElementMatchers.isAbstract;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import io.opentracing.util.GlobalTracer;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.camel.CamelContext;
import org.apache.camel.opentracing.OpenTracingTracer;

@AutoService(Instrumenter.class)
public class CamelContextInstrumentation extends Instrumenter.Default {

  public CamelContextInstrumentation() {
    super("apachecamel", "apache-camel");
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderMatcher() {
    // Optimization for expensive typeMatcher.
    return hasClassesNamed("org.apache.camel.CamelContext");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {

    return not(isAbstract()).and(implementsInterface(named("org.apache.camel.CamelContext")));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      getClass().getName() + "$ContextAdvice",
      "org.apache.camel.opentracing.propagation.CamelHeadersExtractAdapter",
      "org.apache.camel.opentracing.propagation.CamelHeadersInjectAdapter",
      "org.apache.camel.opentracing.SpanDecorator",
      "org.apache.camel.opentracing.decorators.AbstractSpanDecorator",
      "org.apache.camel.opentracing.decorators.DefaultSpanDecorator",
      "org.apache.camel.opentracing.decorators.AbstractHttpSpanDecorator",
      "org.apache.camel.opentracing.decorators.AbstractInternalSpanDecorator",
      "org.apache.camel.opentracing.decorators.AbstractMessagingSpanDecorator",
      "org.apache.camel.opentracing.decorators.AhcSpanDecorator",
      "org.apache.camel.opentracing.decorators.AmqpSpanDecorator",
      "org.apache.camel.opentracing.decorators.AwsSnsSpanDecorator",
      "org.apache.camel.opentracing.decorators.AwsSqsSpanDecorator",
      "org.apache.camel.opentracing.decorators.CometdSpanDecorator",
      "org.apache.camel.opentracing.decorators.CometdsSpanDecorator",
      "org.apache.camel.opentracing.decorators.CqlSpanDecorator",
      "org.apache.camel.opentracing.decorators.DirectSpanDecorator",
      "org.apache.camel.opentracing.decorators.DirectvmSpanDecorator",
      "org.apache.camel.opentracing.decorators.DisruptorSpanDecorator",
      "org.apache.camel.opentracing.decorators.DisruptorvmSpanDecorator",
      "org.apache.camel.opentracing.decorators.ElasticsearchSpanDecorator",
      "org.apache.camel.opentracing.decorators.Http4SpanDecorator",
      "org.apache.camel.opentracing.decorators.HttpSpanDecorator",
      "org.apache.camel.opentracing.decorators.IronmqSpanDecorator",
      "org.apache.camel.opentracing.decorators.JdbcSpanDecorator",
      "org.apache.camel.opentracing.decorators.JettySpanDecorator",
      "org.apache.camel.opentracing.decorators.JmsSpanDecorator",
      "org.apache.camel.opentracing.decorators.KafkaSpanDecorator",
      "org.apache.camel.opentracing.decorators.LogSpanDecorator",
      "org.apache.camel.opentracing.decorators.MongoDBSpanDecorator",
      "org.apache.camel.opentracing.decorators.MqttSpanDecorator",
      "org.apache.camel.opentracing.decorators.NettyHttp4SpanDecorator",
      "org.apache.camel.opentracing.decorators.NettyHttpSpanDecorator",
      "org.apache.camel.opentracing.decorators.PahoSpanDecorator",
      "org.apache.camel.opentracing.decorators.RabbitmqSpanDecorator",
      "org.apache.camel.opentracing.decorators.RestletSpanDecorator",
      "org.apache.camel.opentracing.decorators.RestSpanDecorator",
      "org.apache.camel.opentracing.decorators.SedaSpanDecorator",
      "org.apache.camel.opentracing.decorators.ServletSpanDecorator",
      "org.apache.camel.opentracing.decorators.SjmsSpanDecorator",
      "org.apache.camel.opentracing.decorators.SqlSpanDecorator",
      "org.apache.camel.opentracing.decorators.StompSpanDecorator",
      "org.apache.camel.opentracing.decorators.TimerSpanDecorator",
      "org.apache.camel.opentracing.decorators.UndertowSpanDecorator",
      "org.apache.camel.opentracing.decorators.VmSpanDecorator",
      "org.apache.camel.opentracing.decorators.DecoratorRegistry",
      "org.apache.camel.opentracing.ActiveSpanManager",
      "org.apache.camel.opentracing.ActiveSpanManager$SpanWithScope",
      "org.apache.camel.opentracing.OpenTracingTracer",
      "org.apache.camel.opentracing.OpenTracingTracer$OpenTracingEventNotifier",
      "org.apache.camel.opentracing.OpenTracingTracer$OpenTracingRoutePolicy"
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    final Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();

    transformers.put(
        named("start").and(isPublic()).and(takesArguments(0)),
        CamelContextInstrumentation.class.getName() + "$ContextAdvice");

    return transformers;
  }

  public static class ContextAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodExit(@Advice.This final CamelContext context) {
      (new OpenTracingTracer(GlobalTracer.get())).init(context);
    }
  }
}
