package datadog.trace.instrumentation.jersey;

import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.glassfish.jersey.server.ResourceConfig;

@AutoService(Instrumenter.class)
public final class JerseyInstrumentation extends Instrumenter.Default {

  public JerseyInstrumentation() {
    super("jersey");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.glassfish.jersey.server.ResourceConfig");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".JerseyRequestExtractAdapter",
      packageName + ".JerseyRequestExtractAdapter$MultivaluedMapIterator",
      packageName + ".TracingRequestEventListener",
      packageName + ".TracingRequestEventListener$1",
      packageName + ".TracingApplicationEventListener"
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isConstructor().and(isPublic()).and(takesArguments(0)),
        JerseyResourceConfigAdvice.class.getName());
  }

  public static class JerseyResourceConfigAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void ingressProcessEnter(@Advice.This final ResourceConfig resourceConfig) {
      resourceConfig.register(TracingApplicationEventListener.class);
    }
  }
}
