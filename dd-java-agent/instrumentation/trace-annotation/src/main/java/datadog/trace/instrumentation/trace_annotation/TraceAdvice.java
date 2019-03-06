// Modified by SignalFx
package datadog.trace.instrumentation.trace_annotation;

import static io.opentracing.log.Fields.ERROR_OBJECT;

import datadog.trace.api.Trace;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Collections;
import net.bytebuddy.asm.Advice;

public class TraceAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static Scope startSpan(@Advice.Origin final Method method) {
    Annotation trace = method.getAnnotation(Trace.class);
    if (trace == null) {
      trace = method.getAnnotation(com.signalfx.tracing.api.Trace.class);
    }
    String operationName = null;
    if (trace != null) {
      // Java annotations do not support polymorphism.
      if (trace.annotationType().equals(Trace.class)) {
        operationName = ((Trace) trace).operationName();
      } else {
        operationName = ((com.signalfx.tracing.api.Trace) trace).operationName();
      }
    }
    if (operationName == null || operationName.isEmpty()) {
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
      operationName = className + "." + method.getName();
    }

    return GlobalTracer.get()
        .buildSpan(operationName)
        .withTag(Tags.COMPONENT.getKey(), "trace")
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
