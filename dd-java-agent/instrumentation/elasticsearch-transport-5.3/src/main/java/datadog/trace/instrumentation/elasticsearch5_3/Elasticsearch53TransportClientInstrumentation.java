package datadog.trace.instrumentation.elasticsearch5_3;

import static io.opentracing.log.Fields.ERROR_OBJECT;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.api.DDSpanTypes;
import datadog.trace.api.DDTags;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.util.Collections;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.elasticsearch.action.Action;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.action.ActionResponse;

/** Beginning in version 5.3.0, DocumentRequest was renamed to DocWriteRequest. */
@AutoService(Instrumenter.class)
public class Elasticsearch53TransportClientInstrumentation extends Instrumenter.Default {

  public Elasticsearch53TransportClientInstrumentation() {
    super("elasticsearch", "elasticsearch-transport", "elasticsearch-transport-5");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    // If we want to be more generic, we could instrument the interface instead:
    // .and(safeHasSuperType(named("org.elasticsearch.client.ElasticsearchClient"))))
    return not(isInterface()).and(named("org.elasticsearch.client.support.AbstractClient"));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "com.google.common.base.Preconditions",
      "com.google.common.base.Joiner",
      "com.google.common.base.Joiner$1",
      "com.google.common.base.Joiner$2",
      "com.google.common.base.Joiner$MapJoiner",
      "datadog.trace.instrumentation.elasticsearch5_3.TransportActionListener"
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod()
            .and(named("execute"))
            .and(takesArgument(0, named("org.elasticsearch.action.Action")))
            .and(takesArgument(1, named("org.elasticsearch.action.ActionRequest")))
            .and(takesArgument(2, named("org.elasticsearch.action.ActionListener"))),
        ElasticsearchTransportClientAdvice.class.getName());
  }

  public static class ElasticsearchTransportClientAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Scope startSpan(
        @Advice.Argument(0) final Action action,
        @Advice.Argument(1) final ActionRequest actionRequest,
        @Advice.Argument(value = 2, readOnly = false)
            ActionListener<ActionResponse> actionListener) {

      final Scope scope =
          GlobalTracer.get()
              .buildSpan("elasticsearch.query")
              .withTag(DDTags.SERVICE_NAME, "elasticsearch")
              .withTag(DDTags.RESOURCE_NAME, action.getClass().getSimpleName())
              .withTag(DDTags.SPAN_TYPE, DDSpanTypes.ELASTICSEARCH)
              .withTag(Tags.COMPONENT.getKey(), "elasticsearch-java")
              .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
              .withTag("elasticsearch.action", action.getClass().getSimpleName())
              .withTag("elasticsearch.request", actionRequest.getClass().getSimpleName())
              .startActive(false);

      actionListener = new TransportActionListener<>(actionRequest, actionListener, scope.span());
      return scope;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Enter final Scope scope, @Advice.Thrown final Throwable throwable) {
      if (throwable != null) {
        final Span span = scope.span();
        Tags.ERROR.set(span, true);
        span.log(Collections.singletonMap(ERROR_OBJECT, throwable));
        span.finish();
      }
      scope.close();
    }
  }
}
