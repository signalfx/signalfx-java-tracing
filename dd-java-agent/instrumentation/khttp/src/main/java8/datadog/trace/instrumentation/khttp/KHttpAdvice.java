// Modified by SignalFx
package datadog.trace.instrumentation.khttp;

import static datadog.trace.instrumentation.khttp.KHttpHeadersInjectAdapter.SETTER;
import static datadog.trace.instrumentation.khttp.KHttpDecorator.DECORATE;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.propagate;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.startSpan;

import datadog.trace.bootstrap.CallDepthThreadLocalMap;
import datadog.trace.bootstrap.instrumentation.api.AgentScope;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import java.util.HashMap;
import java.util.Map;
import khttp.KHttp;
import khttp.requests.Request;
import khttp.responses.Response;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;

public class KHttpAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static AgentScope methodEnter(
      @Advice.Argument(
              value = 2,
              optional = true,
              typing = Assigner.Typing.DYNAMIC,
              readOnly = false)
          Map headers) {
    final int callDepth = CallDepthThreadLocalMap.incrementCallDepth(KHttp.class);
    if (callDepth > 0) {
      return null;
    }

    final AgentSpan span = startSpan("http.request");
    DECORATE.afterStart(span);

    boolean awsClientCall = false;
    for (final Object obj : headers.keySet()) {
      if (obj instanceof String) {
        if (((String) obj).equalsIgnoreCase("amz-sdk-invocation-id")) {
          awsClientCall = true;
          break;
        }
      }
    }

    if (!awsClientCall) {
      Class emptyMap = KHttpAdviceUtils.emptyMap;
      if (emptyMap != null && emptyMap.isInstance(headers)) {
        // EmptyMap is read-only so we need to overwrite default arg for header injection
        headers = new HashMap<String, String>();
      }

      propagate().inject(span, headers, SETTER);
    }
    return activateSpan(span, true);
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void methodExit(
      @Advice.Enter final AgentScope scope,
      @Advice.Return final Response response,
      @Advice.Thrown final Throwable throwable) {
    CallDepthThreadLocalMap.reset(KHttp.class);

    if (scope != null) {
      try {
        final AgentSpan span = scope.span();
        Request request = response.getRequest();
        DECORATE.onRequest(span, request);
        DECORATE.onResponse(span, response);
        DECORATE.onError(span, throwable);
        DECORATE.beforeFinish(span);
      } finally {
        scope.close();
      }
    }
  }
}
