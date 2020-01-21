// Modified by SignalFx
package datadog.trace.instrumentation.springweb;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestTemplate;

@AutoService(Instrumenter.class)
public class RestTemplateInstrumentation extends Instrumenter.Default {

  public RestTemplateInstrumentation() {
    super("spring-web", "resttemplate", "rest-template");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.springframework.web.client.RestTemplate");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "datadog.trace.instrumentation.springweb.TracingInterceptor",
      "datadog.trace.instrumentation.springweb.InjectAdapter",
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    final Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();
    transformers.put(
        isConstructor()
            .and(isPublic())
            .and(takesArgument(0, java.util.List.class).or(takesArguments(0))),
        RestTemplateAdvice.class.getName());
    return transformers;
  }

  public static class RestTemplateAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void addTracingInterceptor(@Advice.This final RestTemplate restTemplate) {
      ArrayList<ClientHttpRequestInterceptor> interceptors =
          (ArrayList<ClientHttpRequestInterceptor>) restTemplate.getInterceptors();
      for (final ClientHttpRequestInterceptor interceptor : interceptors) {
        if (interceptor instanceof TracingInterceptor) {
          return;
        }
      }
      final TracingInterceptor interceptor = new TracingInterceptor();
      interceptors.add(interceptor);
      restTemplate.setInterceptors(interceptors);
    }
  }
}
