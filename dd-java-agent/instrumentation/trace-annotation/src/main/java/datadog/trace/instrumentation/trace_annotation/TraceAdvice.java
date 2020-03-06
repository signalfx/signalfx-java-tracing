// Modified by SignalFx
package datadog.trace.instrumentation.trace_annotation;

import static datadog.trace.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.instrumentation.api.AgentTracer.startSpan;
import static datadog.trace.instrumentation.trace_annotation.TraceAnnotationUtils.classMethodBlacklist;
import static datadog.trace.instrumentation.trace_annotation.TraceDecorator.DECORATE;

import datadog.trace.api.DDTags;
import datadog.trace.api.Trace;
import datadog.trace.instrumentation.api.AgentScope;
import datadog.trace.instrumentation.api.AgentSpan;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Set;
import net.bytebuddy.asm.Advice;

public class TraceAdvice {
  private static final String DEFAULT_OPERATION_NAME = "trace.annotation";

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static AgentScope onEnter(
      @Advice.Origin final Method method,
      @Advice.Origin("#t") final String cls,
      @Advice.Origin("#m") final String name) {
    final Set<String> methods = classMethodBlacklist.get(cls);
    if (methods != null) {
      if (methods.contains(name)) {
        return null;
      }
    }

    String resourceName = DECORATE.spanNameForMethod(method);
    String operationName = resourceName;
    String annotatedOperationName = null;

    Annotation trace = method.getAnnotation(Trace.class);
    if (trace != null) {
      // DD Trace annotation defaults to generic operation name
      operationName = DEFAULT_OPERATION_NAME;

      final Trace traceAnnotation = (Trace) trace;
      annotatedOperationName = traceAnnotation.operationName();
      String annotatedResource = traceAnnotation.resourceName();
      if (annotatedResource != null && !annotatedResource.isEmpty()) {
        resourceName = annotatedResource;
      }
    } else {
      trace = method.getAnnotation(com.signalfx.tracing.api.Trace.class);
      if (trace != null) {
        annotatedOperationName = ((com.signalfx.tracing.api.Trace) trace).operationName();
      }
    }

    if (annotatedOperationName != null && !annotatedOperationName.isEmpty()) {
      operationName = annotatedOperationName;
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
    if (scope == null) {
      return;
    }
    DECORATE.onError(scope, throwable);
    DECORATE.beforeFinish(scope);
    scope.close();
  }
}
