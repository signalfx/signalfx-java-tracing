// Modified by SignalFx
package dd.test.trace.annotation;

import datadog.trace.api.Trace;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.util.concurrent.Callable;

public class SayTracedHello {

  @Trace
  public static String sayHello() {
    return "hello!";
  }

  @Trace(operationName = "SAY_HA")
  public static String sayHA() {
    Tags.SPAN_KIND.set(GlobalTracer.get().scopeManager().active().span(), "DB");
    return "HA!!";
  }

  @Trace(operationName = "NEW_TRACE")
  public static String sayHELLOsayHA() {
    return sayHello() + sayHA();
  }

  @Trace(operationName = "ERROR")
  public static String sayERROR() {
    throw new RuntimeException();
  }

  public static String fromCallable() throws Exception {
    return new Callable<String>() {
      @com.newrelic.api.agent.Trace
      @Override
      public String call() throws Exception {
        return "Howdy!";
      }
    }.call();
  }

  public static String fromCallableWhenDisabled() throws Exception {
    return new Callable<String>() {
      @com.newrelic.api.agent.Trace
      @Override
      public String call() throws Exception {
        return "Howdy!";
      }
    }.call();
  }
}
