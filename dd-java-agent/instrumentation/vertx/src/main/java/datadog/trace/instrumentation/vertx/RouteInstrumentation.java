// Modified by SignalFx
package datadog.trace.instrumentation.vertx;

import static datadog.trace.agent.tooling.bytebuddy.matcher.DDElementMatchers.safeHasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public final class RouteInstrumentation extends Instrumenter.Default {

  public RouteInstrumentation() {
    super("vertx", "vert.x");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return not(isInterface()).and(safeHasSuperType(named("io.vertx.ext.web.Route")));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".RoutingContextDecorator", packageName + ".RoutingContextHandlerWrapper",
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    Map adviceMap = new HashMap();
    adviceMap.put(
        isMethod().and(named("handler")).and(takesArgument(0, named("io.vertx.core.Handler"))),
        packageName + ".RouteAdvice");
    return adviceMap;
  }
}
