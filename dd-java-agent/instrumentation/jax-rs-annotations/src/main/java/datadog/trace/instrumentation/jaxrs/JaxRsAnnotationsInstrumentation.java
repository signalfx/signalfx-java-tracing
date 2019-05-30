// Modified by SignalFx
package datadog.trace.instrumentation.jaxrs;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static datadog.trace.instrumentation.jaxrs.JaxRsAnnotationsDecorator.DECORATE;
import static io.opentracing.log.Fields.ERROR_OBJECT;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.declaresMethod;
import static net.bytebuddy.matcher.ElementMatchers.isAnnotatedWith;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import com.signalfx.tracing.api.TraceSetting;
import datadog.trace.agent.tooling.Instrumenter;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public final class JaxRsAnnotationsInstrumentation extends Instrumenter.Default {

  public JaxRsAnnotationsInstrumentation() {
    super("jax-rs", "jaxrs", "jax-rs-annotations");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return safeHasSuperType(
        isAnnotatedWith(named("javax.ws.rs.Path"))
            .or(safeHasSuperType(declaresMethod(isAnnotatedWith(named("javax.ws.rs.Path"))))));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "datadog.trace.agent.decorator.BaseDecorator", packageName + ".JaxRsAnnotationsDecorator",
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isAnnotatedWith(
            named("javax.ws.rs.Path")
                .or(named("javax.ws.rs.DELETE"))
                .or(named("javax.ws.rs.GET"))
                .or(named("javax.ws.rs.HEAD"))
                .or(named("javax.ws.rs.OPTIONS"))
                .or(named("javax.ws.rs.POST"))
                .or(named("javax.ws.rs.PUT"))),
        JaxRsAnnotationsAdvice.class.getName());
  }

  public static class JaxRsAnnotationsAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Stack<Scope> nameSpan(@Advice.Origin final Method method) {

      final Class<?> clazz = method.getDeclaringClass();
      final String methodName = method.getName();

      String className = clazz.getSimpleName();
      if (className.isEmpty()) {
        className = clazz.getName();
        if (clazz.getPackage() != null) {
          final String pkgName = clazz.getPackage().getName();
          if (!pkgName.isEmpty()) {
            className = clazz.getName().replace(pkgName, "").substring(1);
          }
        }
      }

      final String operationName = className + "." + methodName;

      final Stack<Scope> scopeStack = new Stack<>();

      Scope parentScope = GlobalTracer.get().scopeManager().active();
      if (parentScope == null) {
        parentScope = GlobalTracer.get().buildSpan(operationName).startActive(true);
        scopeStack.push(parentScope);
      }

      // Rename the parent span according to the path represented by these annotations.
      DECORATE.updateParent(parentScope, method);

      // Now create a span representing the method execution.
      final Scope controllerScope = GlobalTracer.get().buildSpan(operationName).startActive(true);
      scopeStack.push(controllerScope);
      DECORATE.afterStart(controllerScope);

      return scopeStack;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Origin final Method method,
        @Advice.Enter final Stack<Scope> scopes,
        @Advice.Thrown final Throwable throwable) {
      if (throwable != null) {
        List<Class> allowedExceptions = TraceSetting.Annotated.getAllowedExceptions(method);

        Boolean setErrorTag = true;
        for (Class allowed : allowedExceptions) {
          if (allowed.isInstance(throwable)) {
            setErrorTag = false;
            break;
          }
        }

        if (setErrorTag) {
          final Span span = scopes.peek().span();
          Tags.ERROR.set(span, true);
          span.log(Collections.singletonMap(ERROR_OBJECT, throwable));
        }
      }

      while (!scopes.isEmpty()) {
        scopes.pop().close();
      }
    }
  }
}
