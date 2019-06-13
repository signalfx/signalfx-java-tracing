// Modified by SignalFx
package datadog.trace.instrumentation.jetty6;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
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
public final class HandlerInstrumentation extends Instrumenter.Default {

  public HandlerInstrumentation() {
    super("jetty", "jetty-6");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return not(isInterface())
        .and(safeHasSuperType(named("org.mortbay.jetty.Handler")))
        .and(not(named("org.mortbay.jetty.handler.HandlerWrapper")));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "datadog.trace.agent.decorator.BaseDecorator",
      "datadog.trace.agent.decorator.ServerDecorator",
      "datadog.trace.agent.decorator.HttpServerDecorator",
      packageName + ".JettyDecorator",
      packageName + ".ServletHeaderAdapter",
      packageName + ".ServletHeaderAdapter$MultivaluedMapFlatIterator",
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        named("handle")
            .and(takesArgument(0, named("java.lang.String")))
            .and(takesArgument(1, named("javax.servlet.http.HttpServletRequest")))
            .and(takesArgument(2, named("javax.servlet.http.HttpServletResponse")))
            .and(takesArgument(3, int.class))
            .and(isPublic()),
        JettyHandlerAdvice.class.getName());
  }
}
