// Modified by SignalFx
package datadog.trace.instrumentation.trace_annotation;

import datadog.trace.api.Config;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TraceAnnotationUtils {

  public static final Map<String, Set<String>> classMethodBlacklist =
      getClassMethodMap(Config.get().getAnnotatedMethodBlacklist());

  static final String PACKAGE_CLASS_NAME_REGEX = "[\\w.\\$]+";
  private static final String METHOD_LIST_REGEX = "\\s*(?:\\w+\\s*,)*\\s*(?:\\w+\\s*,?)\\s*";
  private static final String CONFIG_FORMAT =
      "(?:\\s*"
          + PACKAGE_CLASS_NAME_REGEX
          + "\\["
          + METHOD_LIST_REGEX
          + "\\]\\s*;)*\\s*"
          + PACKAGE_CLASS_NAME_REGEX
          + "\\["
          + METHOD_LIST_REGEX
          + "\\]\\s*;?\\s*";

  public static Map<String, Set<String>> getClassMethodMap(final String configString) {
    if (configString == null || configString.trim().isEmpty()) {
      return Collections.emptyMap();
    } else if (!configString.matches(CONFIG_FORMAT)) {
      log.warn(
          "Invalid trace method config '{}'. Must match 'package.Class$Name[method1,method2];*'.",
          configString);
      return Collections.emptyMap();
    } else {
      final Map<String, Set<String>> toTrace = new HashMap<>(0);
      final String[] classMethods = configString.split(";", -1);
      for (final String classMethod : classMethods) {
        if (classMethod.trim().isEmpty()) {
          continue;
        }
        final String[] splitClassMethod = classMethod.split("\\[", -1);
        final String className = splitClassMethod[0];
        final String method = splitClassMethod[1].trim();
        final String methodNames = method.substring(0, method.length() - 1);
        final String[] splitMethodNames = methodNames.split(",", -1);
        final Set<String> trimmedMethodNames = new HashSet<>(splitMethodNames.length);
        for (final String methodName : splitMethodNames) {
          final String trimmedMethodName = methodName.trim();
          if (!trimmedMethodName.isEmpty()) {
            trimmedMethodNames.add(trimmedMethodName);
          }
        }
        if (!trimmedMethodNames.isEmpty()) {
          toTrace.put(className.trim(), trimmedMethodNames);
        }
      }
      return Collections.unmodifiableMap(toTrace);
    }
  }
}
