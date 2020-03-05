// Modified by SignalFx
package datadog.trace.instrumentation.trace_annotation;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static datadog.trace.instrumentation.trace_annotation.TraceAnnotationUtils.getClassMethodMap;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.api.Config;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * TraceConfig Instrumentation does not extend Default.
 *
 * <p>Instead it directly implements Instrumenter#instrument() and adds one default Instrumenter for
 * every configured class+method-list.
 *
 * <p>If this becomes a more common use case the building logic should be abstracted out into a
 * super class.
 */
@Slf4j
@AutoService(Instrumenter.class)
public class TraceConfigInstrumentation implements Instrumenter {

  private final Map<String, Set<String>> classMethodsToTrace;

  public TraceConfigInstrumentation() {
    final String configString = Config.get().getTraceMethods();
    classMethodsToTrace = getClassMethodMap(configString);
  }

  @Override
  public AgentBuilder instrument(AgentBuilder agentBuilder) {
    if (classMethodsToTrace.isEmpty()) {
      return agentBuilder;
    }

    for (final Map.Entry<String, Set<String>> entry : classMethodsToTrace.entrySet()) {
      final TracerClassInstrumentation tracerConfigClass =
          new TracerClassInstrumentation(entry.getKey(), entry.getValue());
      agentBuilder = tracerConfigClass.instrument(agentBuilder);
    }
    return agentBuilder;
  }

  // Not Using AutoService to hook up this instrumentation
  public static class TracerClassInstrumentation extends Default {
    private final String className;
    private final Set<String> methodNames;

    /** No-arg constructor only used by muzzle and tests. */
    public TracerClassInstrumentation() {
      this("noop", Collections.singleton("noop"));
    }

    public TracerClassInstrumentation(final String className, final Set<String> methodNames) {
      super("trace", "trace-config");
      this.className = className;
      this.methodNames = methodNames;
    }

    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      return safeHasSuperType(named(className));
    }

    @Override
    public String[] helperClassNames() {
      return new String[] {
        "datadog.trace.agent.decorator.BaseDecorator",
        packageName + ".TraceAnnotationUtils",
        packageName + ".TraceDecorator",
      };
    }

    @Override
    public Map<ElementMatcher<? super MethodDescription>, String> transformers() {
      ElementMatcher.Junction<MethodDescription> methodMatchers = null;
      for (final String methodName : methodNames) {
        if (methodMatchers == null) {
          methodMatchers = named(methodName);
        } else {
          methodMatchers = methodMatchers.or(named(methodName));
        }
      }

      final Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();
      transformers.put(methodMatchers, TraceAdvice.class.getName());
      return transformers;
    }
  }
}
