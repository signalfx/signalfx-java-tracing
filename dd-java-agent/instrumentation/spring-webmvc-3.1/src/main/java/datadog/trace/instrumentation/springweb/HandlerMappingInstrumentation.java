package datadog.trace.instrumentation.springweb;

import static datadog.trace.agent.tooling.ClassLoaderMatcher.hasClassesNamed;
import static datadog.trace.agent.tooling.bytebuddy.matcher.DDElementMatchers.implementsInterface;
import static datadog.trace.bootstrap.instrumentation.decorator.HttpServerDecorator.DD_SPAN_ATTRIBUTE;
import static datadog.trace.instrumentation.springweb.SpringWebHttpServerDecorator.DECORATE;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Instruments {@link org.springframework.web.servlet.HandlerMapping} to update SERVER span name
 * based on the mapping string of the Spring controller the was chosen to handle given request.
 */
@AutoService(Instrumenter.class)
public final class HandlerMappingInstrumentation extends Instrumenter.Default {

  public HandlerMappingInstrumentation() {
    super("spring-web");
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderMatcher() {
    // Optimization for expensive typeMatcher.
    return hasClassesNamed("org.springframework.web.servlet.HandlerMapping");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("org.springframework.web.servlet.HandlerMapping"));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".SpringWebHttpServerDecorator",
      packageName + ".SpringWebHttpServerDecorator$1",
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod()
            .and(named("getHandler"))
            .and(takesArgument(0, named("javax.servlet.http.HttpServletRequest")))
            .and(takesArguments(1)),
        HandlerMappingInstrumentation.class.getName() + "$HandlerMappingAdvice");
  }

  public static class HandlerMappingAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void renameParentSpan(
        @Advice.Argument(0) final HttpServletRequest request,
        @Advice.Return final Object handlerChain) {
      if (handlerChain != null) {
        // Most probably instrumented HandlerMapping has set matched pattern to the request.
        // Set that pattern as name of the parent span.
        // We assume that paren span == SERVER span.
        final Object parentSpan = request.getAttribute(DD_SPAN_ATTRIBUTE);
        if (parentSpan instanceof AgentSpan) {
          DECORATE.onRequest((AgentSpan) parentSpan, request);
        }
      }
    }
  }
}
