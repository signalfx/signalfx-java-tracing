package datadog.trace.instrumentation.jaxrs;

import static datadog.trace.bootstrap.WeakMap.Provider.newWeakMap;

import datadog.trace.agent.decorator.BaseDecorator;
import datadog.trace.bootstrap.WeakMap;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.tag.Tags;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.Path;

public class JaxRsAnnotationsDecorator extends BaseDecorator {
  public static JaxRsAnnotationsDecorator DECORATE = new JaxRsAnnotationsDecorator();

  private final WeakMap<Class, Map<Method, String>> resourceNames = newWeakMap();
  private final WeakMap<Class, String> urls = newWeakMap();

  @Override
  protected String[] instrumentationNames() {
    return new String[0];
  }

  @Override
  protected String spanType() {
    return null;
  }

  @Override
  protected String component() {
    return "jax-rs-controller";
  }

  public void updateParent(final Scope scope, final Method method) {
    if (scope == null) {
      return;
    }
    final Span span = scope.span();
    Tags.COMPONENT.set(span, "jax-rs");
    Tags.SPAN_KIND.set(span, Tags.SPAN_KIND_SERVER);

    final Class<?> target = method.getDeclaringClass();
    Map<Method, String> classMap = resourceNames.get(target);
    String url = urls.get(target);

    if (classMap == null) {
      resourceNames.putIfAbsent(target, new ConcurrentHashMap<Method, String>());
      classMap = resourceNames.get(target);
      // classMap should not be null at this point because we have a
      // strong reference to target and don't manually clear the map.
    }

    final String httpMethod = locateHttpMethod(method);

    String resourceName = classMap.get(method);
    if (resourceName == null) {
      final LinkedList<Path> paths = gatherPaths(method);
      resourceName = buildResourceName(httpMethod, paths);
      classMap.put(method, resourceName);

      if (url == null) {
        url = buildUrl(paths);
        urls.put(target, url);
      }
    }

    if (!url.isEmpty()) {
      Tags.HTTP_URL.set(span, url);
    }

    if (httpMethod != null) {
      Tags.HTTP_METHOD.set(span, httpMethod);
    }

    if (!resourceName.isEmpty()) {
      span.setOperationName(resourceName);
    }
  }

  private String locateHttpMethod(final Method method) {
    String httpMethod = null;
    for (final Annotation ann : method.getDeclaredAnnotations()) {
      if (ann.annotationType().getAnnotation(HttpMethod.class) != null) {
        httpMethod = ann.annotationType().getSimpleName();
      }
    }
    return httpMethod;
  }

  private LinkedList<Path> gatherPaths(final Method method) {
    Class<?> target = method.getDeclaringClass();
    final LinkedList<Path> paths = new LinkedList<>();
    while (target != Object.class) {
      final Path annotation = target.getAnnotation(Path.class);
      if (annotation != null) {
        paths.push(annotation);
      }
      target = target.getSuperclass();
    }
    final Path methodPath = method.getAnnotation(Path.class);
    if (methodPath != null) {
      paths.add(methodPath);
    }
    return paths;
  }

  private String buildResourceName(final String httpMethod, final LinkedList<Path> paths) {
    final String resourceName;
    final StringBuilder resourceNameBuilder = new StringBuilder();
    if (httpMethod != null) {
      resourceNameBuilder.append(httpMethod);
      resourceNameBuilder.append(" ");
    }
    Path last = null;
    for (final Path path : paths) {
      if (path.value().startsWith("/") || (last != null && last.value().endsWith("/"))) {
        resourceNameBuilder.append(path.value());
      } else {
        resourceNameBuilder.append("/");
        resourceNameBuilder.append(path.value());
      }
      last = path;
    }
    resourceName = resourceNameBuilder.toString().trim();
    return resourceName;
  }

  private String buildUrl(final LinkedList<Path> paths) {
    final StringBuilder urlBuilder = new StringBuilder();
    Path last = null;
    for (final Path path : paths) {
      if (!path.value().startsWith("/") && !(last != null && last.value().endsWith("/"))) {
        urlBuilder.append("/");
      }
      urlBuilder.append(path.value());
      last = path;
    }
    return urlBuilder.toString().trim();
  }
}
