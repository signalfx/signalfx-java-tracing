// Modified by SignalFx
package datadog.trace.instrumentation.trace_annotation;

import static datadog.trace.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.instrumentation.api.AgentTracer.startSpan;
import static datadog.trace.instrumentation.trace_annotation.TraceDecorator.DECORATE;

import datadog.trace.api.DDTags;
import datadog.trace.api.Trace;
import datadog.trace.instrumentation.api.AgentScope;
import datadog.trace.instrumentation.api.AgentSpan;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import net.bytebuddy.asm.Advice;

public class TraceAdvice {
  private static final String DEFAULT_OPERATION_NAME = "trace.annotation";

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static AgentScope onEnter(@Advice.Origin final Method method) {
    Annotation trace = method.getAnnotation(Trace.class);
    if (trace == null) {
      trace = method.getAnnotation(com.signalfx.tracing.api.Trace.class);
    }

    boolean useResourceAsOperation = false;
    String operationName = null;
    String resourceName = null;
    if (trace != null) {
      // Java annotations do not support polymorphism.
      if (trace.annotationType().equals(Trace.class)) {
        final Trace traceAnnotation = (Trace) trace;
        operationName = traceAnnotation.operationName();
        resourceName = traceAnnotation.resourceName();
      } else {
        operationName = ((com.signalfx.tracing.api.Trace) trace).operationName();
        if (operationName == null || operationName.isEmpty()) {
          useResourceAsOperation = true;
        }
      }
    }

    if (resourceName == null || resourceName.isEmpty()) {
      resourceName = DECORATE.spanNameForMethod(method);
    }

    if (operationName == null || operationName.isEmpty()) {
      operationName = useResourceAsOperation ? resourceName : DEFAULT_OPERATION_NAME;
    }

    final AgentSpan span = startSpan(operationName);
    span.setTag(DDTags.RESOURCE_NAME, resourceName);
    DECORATE.afterStart(span);

    final AgentScope scope = activateSpan(span, true);
    scope.setAsyncPropagation(true);
    return scope;
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Enter final AgentScope scope, @Advice.Thrown final Throwable throwable) {
    DECORATE.onError(scope, throwable);
    DECORATE.beforeFinish(scope);
    scope.close();
  }
}
