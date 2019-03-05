package datadog.trace.instrumentation.hystrix;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static io.opentracing.log.Fields.ERROR_OBJECT;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.api.DDTags;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public class HystrixCommandInstrumentation extends Instrumenter.Default {

  private static final String OPERATION_NAME = "hystrix.cmd";

  public HystrixCommandInstrumentation() {
    super("hystrix");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    // Not adding a version restriction because this should work with any version and add some
    // benefit.
    return not(isInterface()).and(safeHasSuperType(named("com.netflix.hystrix.HystrixCommand")));
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod().and(named("run").or(named("getFallback"))), TraceAdvice.class.getName());
  }

  public static class TraceAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Scope startSpan(@Advice.Origin final Method method) {
      final Class<?> declaringClass = method.getDeclaringClass();
      String className = declaringClass.getSimpleName();
      if (className.isEmpty()) {
        className = declaringClass.getName();
        if (declaringClass.getPackage() != null) {
          final String pkgName = declaringClass.getPackage().getName();
          if (!pkgName.isEmpty()) {
            className = declaringClass.getName().replace(pkgName, "").substring(1);
          }
        }
      }
      final String resourceName = className + "." + method.getName();

      return GlobalTracer.get()
          .buildSpan(OPERATION_NAME)
          .withTag(DDTags.RESOURCE_NAME, resourceName)
          .withTag(Tags.COMPONENT.getKey(), "hystrix")
          .startActive(true);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Enter final Scope scope, @Advice.Thrown final Throwable throwable) {
      if (throwable != null) {
        final Span span = scope.span();
        Tags.ERROR.set(span, true);
        span.log(Collections.singletonMap(ERROR_OBJECT, throwable));
      }
      scope.close();
    }
  }
}
