package com.signalfx.tracing.api;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Use this annotation for class and method per-instrumentation configurations */
@Retention(RUNTIME)
@Target({TYPE, METHOD})
public @interface TraceSetting {

  /** Helper class to operate on potentially annotated AnnotatedElements */
  class Annotated {

    /** Retrieves any annotated TraceSettings up the inheritance chain. */
    private static List<TraceSetting> getAllTraceSettings(AnnotatedElement target) {
      ArrayList<TraceSetting> traceSettings = new ArrayList<>();

      final TraceSetting applied = target.getAnnotation(TraceSetting.class);
      if (applied != null) {
        traceSettings.add(applied);
      }

      Class<?> klass;
      if (target instanceof Method) {
        klass = ((Method) target).getDeclaringClass();
      } else if (target instanceof Class) {
        klass = ((Class) target).getSuperclass();
      } else { // Unsupported interface or enum.
        klass = Object.class;
      }

      // Go up the inheritance chain and get all TraceSettings
      while (klass != Object.class) {
        final TraceSetting traceSetting = klass.getAnnotation(TraceSetting.class);
        if (traceSetting != null) {
          traceSettings.add(traceSetting);
        }
        klass = klass.getSuperclass();
      }

      return traceSettings;
    }

    /**
     * Get a list of Exception classes specified by the allowedExceptions element for target, its
     * class (if method), and all super classes.
     */
    public static List<Class> getAllowedExceptions(AnnotatedElement target) {
      List<TraceSetting> traceSettings = getAllTraceSettings(target);
      ArrayList<Class> allowedExceptions = new ArrayList<>();
      for (final TraceSetting traceSetting : traceSettings) {
        allowedExceptions.addAll(Arrays.asList(traceSetting.allowedExceptions()));
      }
      return allowedExceptions;
    }
  }

  /** An array of Exceptions to not error tag in instrumentations. */
  Class[] allowedExceptions() default {};
}
