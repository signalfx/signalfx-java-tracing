// Modified by SignalFx
package datadog.trace.instrumentation.vertx;

import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import java.util.Map;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public class VertxRxInstrumentation extends Instrumenter.Default {

  public VertxRxInstrumentation() {
    super("vertx", "vert.x");
  }

  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {

    return not(isInterface()).and(named("io.vertx.reactivex.impl.AsyncResultSingle"));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".AsyncResultSingleAdvice", packageName + ".AsyncResultHandlerConsumerWrapper"
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod()
            .and(isStatic())
            .and(named("toSingle"))
            .and(takesArgument(0, named("java.util.function.Consumer"))),
        packageName + ".AsyncResultSingleAdvice");
  }
}
