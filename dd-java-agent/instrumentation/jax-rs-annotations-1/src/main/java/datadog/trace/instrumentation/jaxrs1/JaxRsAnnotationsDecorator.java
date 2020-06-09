// Modified by SignalFx
package datadog.trace.instrumentation.jaxrs1;

import static datadog.trace.bootstrap.WeakMap.Provider.newWeakMap;

import datadog.trace.agent.tooling.ClassHierarchyIterable;
import datadog.trace.api.DDSpanTypes;
import datadog.trace.api.DDTags;
import datadog.trace.bootstrap.WeakMap;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.bootstrap.instrumentation.api.Tags;
import datadog.trace.bootstrap.instrumentation.decorator.BaseDecorator;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.Path;

public class JaxRsAnnotationsDecorator extends BaseDecorator {
  public static JaxRsAnnotationsDecorator DECORATE = new JaxRsAnnotationsDecorator();

  private final WeakMap<Class<?>, Map<Method, ResourceInfo>> resourceNames = newWeakMap();

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

  public void onControllerStart(
      final AgentSpan span, final AgentSpan parent, final Class<?> target, final Method method) {
    final ResourceInfo resourceInfo = getPathResourceName(target, method);
    final String httpMethod = resourceInfo.getHttpMethod();
    final String httpUrl = resourceInfo.getHttpUrl();
    updateParent(parent, httpUrl);

    // Set before resource name to avoid url as resource name decorator
    if (!httpUrl.isEmpty()) {
      span.setTag(Tags.HTTP_URL, httpUrl);
    }
    if (httpMethod != null && !httpMethod.isEmpty()) {
      span.setTag(Tags.HTTP_METHOD, httpMethod);
    }

    span.setTag(DDTags.SPAN_TYPE, DDSpanTypes.HTTP_SERVER);

    span.setTag(DDTags.RESOURCE_NAME, DECORATE.spanNameForClass(target) + "." + method.getName());
    // When jax-rs is the root, we want to name using the path, otherwise use the class/method.
    final boolean isRootScope = parent == null;
    if (isRootScope) {
      span.setTag(Tags.SPAN_KIND, Tags.SPAN_KIND_SERVER);
      if (!httpUrl.isEmpty()) {
        span.setTag(DDTags.RESOURCE_NAME, httpUrl);
      }
    }
  }

  private void updateParent(AgentSpan span, final String resourceName) {
    if (span == null) {
      return;
    }
    span = span.getLocalRootSpan();
    span.setTag(Tags.SPAN_KIND, Tags.SPAN_KIND_SERVER);
    span.setTag(Tags.COMPONENT, "jax-rs");

    if (!resourceName.isEmpty()) {
      span.setTag(DDTags.RESOURCE_NAME, resourceName);
    }
  }

  /**
   * Returns the resource name given a JaxRS annotated method. Results are cached so this method can
   * be called multiple times without significantly impacting performance.
   *
   * @return The result can be an empty string but will never be {@code null}.
   */
  private ResourceInfo getPathResourceName(final Class<?> target, final Method method) {
    Map<Method, ResourceInfo> classMap = resourceNames.get(target);
    if (classMap == null) {
      resourceNames.putIfAbsent(target, new ConcurrentHashMap<Method, ResourceInfo>());
      classMap = resourceNames.get(target);
      // classMap should not be null at this point because we have a
      // strong reference to target and don't manually clear the map.
    }

    ResourceInfo resourceInfo = classMap.get(method);
    if (resourceInfo == null) {
      String httpMethod = null;
      Path methodPath = null;
      final Path classPath = findClassPath(target);
      for (final Class currentClass : new ClassHierarchyIterable(target)) {
        final Method currentMethod;
        if (currentClass.equals(target)) {
          currentMethod = method;
        } else {
          currentMethod = findMatchingMethod(method, currentClass.getDeclaredMethods());
        }

        if (currentMethod != null) {
          if (httpMethod == null) {
            httpMethod = locateHttpMethod(currentMethod);
          }
          if (methodPath == null) {
            methodPath = findMethodPath(currentMethod);
          }

          if (httpMethod != null && methodPath != null) {
            break;
          }
        }
      }
      final String httpUrl = buildResourceName(httpMethod, classPath, methodPath);
      resourceInfo = new ResourceInfo(httpMethod, httpUrl);
      classMap.put(method, resourceInfo);
    }

    return resourceInfo;
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

  private Path findMethodPath(final Method method) {
    return method.getAnnotation(Path.class);
  }

  private Path findClassPath(final Class<?> target) {
    for (final Class<?> currentClass : new ClassHierarchyIterable(target)) {
      final Path annotation = currentClass.getAnnotation(Path.class);
      if (annotation != null) {
        // Annotation overridden, no need to continue.
        return annotation;
      }
    }

    return null;
  }

  private Method findMatchingMethod(final Method baseMethod, final Method[] methods) {
    nextMethod:
    for (final Method method : methods) {
      if (!baseMethod.getReturnType().equals(method.getReturnType())) {
        continue;
      }

      if (!baseMethod.getName().equals(method.getName())) {
        continue;
      }

      final Class<?>[] baseParameterTypes = baseMethod.getParameterTypes();
      final Class<?>[] parameterTypes = method.getParameterTypes();
      if (baseParameterTypes.length != parameterTypes.length) {
        continue;
      }
      for (int i = 0; i < baseParameterTypes.length; i++) {
        if (!baseParameterTypes[i].equals(parameterTypes[i])) {
          continue nextMethod;
        }
      }
      return method;
    }
    return null;
  }

  private String buildResourceName(
      final String httpMethod, final Path classPath, final Path methodPath) {
    final String resourceName;
    final StringBuilder resourceNameBuilder = new StringBuilder();
    // SFx will render http.method tag w/ operation name.
    //    if (httpMethod != null) {
    //      resourceNameBuilder.append(httpMethod);
    //      resourceNameBuilder.append(" ");
    //    }
    boolean skipSlash = false;
    if (classPath != null) {
      if (!classPath.value().startsWith("/")) {
        resourceNameBuilder.append("/");
      }
      resourceNameBuilder.append(classPath.value());
      skipSlash = classPath.value().endsWith("/");
    }

    if (methodPath != null) {
      String path = methodPath.value();
      if (skipSlash) {
        if (path.startsWith("/")) {
          path = path.length() == 1 ? "" : path.substring(1);
        }
      } else if (!path.startsWith("/")) {
        resourceNameBuilder.append("/");
      }
      resourceNameBuilder.append(path);
    }

    resourceName = resourceNameBuilder.toString().trim();
    return resourceName;
  }

  public class ResourceInfo {

    private String httpMethod;
    private String httpUrl;

    public ResourceInfo(final String httpMethod, final String httpUrl) {
      this.httpMethod = httpMethod;
      this.httpUrl = httpUrl;
    }

    public String getHttpMethod() {
      return httpMethod;
    }

    public String getHttpUrl() {
      return httpUrl;
    }
  }
}
