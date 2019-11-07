// Modified by SignalFx
package datadog.trace.instrumentation.jedis;

import static datadog.trace.instrumentation.jedis.JedisClientDecorator.DECORATE;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.api.Config;
import io.opentracing.Scope;
import io.opentracing.util.GlobalTracer;
import java.io.UnsupportedEncodingException;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import redis.clients.jedis.Protocol.Command;

@AutoService(Instrumenter.class)
public final class JedisInstrumentation extends Instrumenter.Default {

  private static final String SERVICE_NAME = "redis";
  private static final String COMPONENT_NAME = SERVICE_NAME + "-command";

  public JedisInstrumentation() {
    super("jedis", "redis");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("redis.clients.jedis.Protocol");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "datadog.trace.agent.decorator.BaseDecorator",
      "datadog.trace.agent.decorator.ClientDecorator",
      "datadog.trace.agent.decorator.DatabaseClientDecorator",
      packageName + ".JedisClientDecorator",
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod()
            .and(isPublic())
            .and(named("sendCommand"))
            .and(takesArgument(1, named("redis.clients.jedis.Protocol$Command"))),
        JedisAdvice.class.getName());
    // FIXME: This instrumentation only incorporates sending the command, not processing the result.
  }

  public static class JedisAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Scope startSpan(
        @Advice.Argument(1) final Command command, @Advice.Argument(2) final byte[][] args) {
      final Scope scope = GlobalTracer.get().buildSpan("redis." + command.name()).startActive(true);
      DECORATE.afterStart(scope.span());
      String statement = command.name();
      if (Config.get().isRedisCaptureCommandArguments() && args.length > 0) {
        statement += ":";
        for (int i = 0; i < args.length; i++) {
          try {
            statement += " " + new String(args[i], "UTF-8");
          } catch (UnsupportedEncodingException e) {
          }
        }
      }
      DECORATE.onStatement(scope.span(), statement);
      return scope;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Enter final Scope scope, @Advice.Thrown final Throwable throwable) {
      DECORATE.onError(scope.span(), throwable);
      DECORATE.beforeFinish(scope.span());
      scope.close();
    }
  }
}
