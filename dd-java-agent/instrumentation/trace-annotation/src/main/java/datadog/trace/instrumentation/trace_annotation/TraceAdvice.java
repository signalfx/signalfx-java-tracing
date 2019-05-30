// Modified by SignalFx
package datadog.trace.instrumentation.trace_annotation;

import static datadog.trace.instrumentation.trace_annotation.TraceDecorator.DECORATE;

import datadog.trace.api.Trace;
import io.opentracing.Scope;
import io.opentracing.util.GlobalTracer;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
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
      operationName = DECORATE.spanNameForMethod(method);
    }
    return DECORATE.afterStart(GlobalTracer.get().buildSpan(operationName).startActive(true));
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Enter final Scope scope, @Advice.Thrown final Throwable throwable) {
    DECORATE.onError(scope, throwable);
    DECORATE.beforeFinish(scope);
    scope.close();
  }
}
