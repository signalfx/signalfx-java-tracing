package datadog.trace.instrumentation.jaxrs;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static io.opentracing.log.Fields.ERROR_OBJECT;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.declaresMethod;
import static net.bytebuddy.matcher.ElementMatchers.isAnnotatedWith;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Map;
import java.util.Stack;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.Path;
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

      // TODO: do we need caching for this?
      final LinkedList<Path> paths = new LinkedList<>();
      Class<?> target = method.getDeclaringClass();
      while (target != Object.class) {
        final Path annotation = target.getAnnotation(Path.class);
        if (annotation != null) {
          paths.push(annotation);
        }
        target = target.getSuperclass();
      }
      final Path methodPath = method.getAnnotation(Path.class);
      if (methodPath != null) {
        paths.add(methodPath);
      }
      String httpMethod = null;
      for (final Annotation ann : method.getDeclaredAnnotations()) {
        if (ann.annotationType().getAnnotation(HttpMethod.class) != null) {
          httpMethod = ann.annotationType().getSimpleName();
        }
      }

      final StringBuilder resourceNameBuilder = new StringBuilder();
      if (httpMethod != null) {
        resourceNameBuilder.append(httpMethod);
        resourceNameBuilder.append(" ");
      }

      final StringBuilder urlBuilder = new StringBuilder();
      Path last = null;
      for (final Path path : paths) {
        if (!path.value().startsWith("/") && !(last != null && last.value().endsWith("/"))) {
          resourceNameBuilder.append("/");
          urlBuilder.append("/");
        }
        resourceNameBuilder.append(path.value());
        urlBuilder.append(path.value());
        last = path;
      }

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

      Span span = parentScope.span();
      Tags.SPAN_KIND.set(span, Tags.SPAN_KIND_SERVER);
      Tags.COMPONENT.set(span, "jax-rs");

      final String resourceName = resourceNameBuilder.toString().trim();
      if (!resourceName.isEmpty()) {
        span.setOperationName(resourceName);
      }

      final String url = urlBuilder.toString().trim();
      if (!url.isEmpty()) {
        Tags.HTTP_URL.set(span, url);
      }

      if (httpMethod != null) {
        Tags.HTTP_METHOD.set(span, httpMethod);
      }

      // Now create a span representing the method execution.

      final Scope controllerScope =
          GlobalTracer.get()
              .buildSpan(operationName)
              .withTag(Tags.COMPONENT.getKey(), "jax-rs-controller")
              .startActive(true);

      scopeStack.push(controllerScope);
      return scopeStack;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Enter final Stack<Scope> scopes, @Advice.Thrown final Throwable throwable) {
      if (throwable != null) {
        final Span span = scopes.peek().span();
        Tags.ERROR.set(span, true);
        span.log(Collections.singletonMap(ERROR_OBJECT, throwable));
      }

      while (!scopes.isEmpty()) {
        scopes.pop().close();
      }
    }
  }
}
