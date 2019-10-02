// Modified by SignalFx
package datadog.trace.instrumentation.khttp;

public class KHttpAdviceUtils {
  public static Class emptyMap;

  static {
    try {
      emptyMap = Class.forName("kotlin.collections.EmptyMap");
    } catch (ClassNotFoundException e) {
    }
  }
}
