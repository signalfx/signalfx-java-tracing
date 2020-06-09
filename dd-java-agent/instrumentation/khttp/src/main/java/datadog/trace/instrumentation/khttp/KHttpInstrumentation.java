// Modified by SignalFx
package datadog.trace.instrumentation.khttp;

import static datadog.trace.agent.tooling.bytebuddy.matcher.DDElementMatchers.safeHasSuperType;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isAbstract;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import java.util.Map;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public class KHttpInstrumentation extends Instrumenter.Default {

  public KHttpInstrumentation() {
    super("khttp");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return safeHasSuperType(named("khttp.KHttp"));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".KHttpAdviceUtils",
      packageName + ".KHttpHeadersInjectAdapter",
      packageName + ".KHttpDecorator",
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod()
            .and(not(isAbstract()))
            .and(
                named("request")
                    .and(
                        takesArgument(2, named("java.util.Map"))
                            .and(returns(named("khttp.responses.Response"))))),
        packageName + ".KHttpAdvice");
  }
}
