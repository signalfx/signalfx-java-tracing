// Modified by SignalFx
package datadog.trace.instrumentation.khttp;

import static datadog.trace.instrumentation.khttp.KHttpDecorator.DECORATE;

import datadog.trace.bootstrap.CallDepthThreadLocalMap;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.util.GlobalTracer;
import java.util.HashMap;
import java.util.Map;
import khttp.KHttp;
import khttp.requests.Request;
import khttp.responses.Response;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;

public class KHttpAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static Scope methodEnter(
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
    final Tracer tracer = GlobalTracer.get();
    final Scope scope = tracer.buildSpan("http.request").startActive(true);
    final Span span = scope.span();
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
      tracer.inject(
          span.context(), Format.Builtin.HTTP_HEADERS, new KHttpHeadersInjectAdapter(headers));
    }
    return scope;
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void methodExit(
      @Advice.Enter final Scope scope,
      @Advice.Return final Response response,
      @Advice.Thrown final Throwable throwable) {
    if (scope != null) {
      try {
        final Span span = scope.span();
        Request request = response.getRequest();
        DECORATE.onRequest(span, request);
        DECORATE.onResponse(span, response);
        DECORATE.onError(span, throwable);
        DECORATE.beforeFinish(span);
      } finally {
        scope.close();
        CallDepthThreadLocalMap.reset(KHttp.class);
      }
    }
  }
}
