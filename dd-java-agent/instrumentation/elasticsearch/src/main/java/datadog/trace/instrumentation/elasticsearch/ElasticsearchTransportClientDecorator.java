// Modified by SignalFx
package datadog.trace.instrumentation.elasticsearch;

import datadog.trace.api.DDSpanTypes;
import datadog.trace.api.DDTags;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.bootstrap.instrumentation.decorator.DatabaseClientDecorator;

public class ElasticsearchTransportClientDecorator extends DatabaseClientDecorator {
  public static final ElasticsearchTransportClientDecorator DECORATE =
      new ElasticsearchTransportClientDecorator();

  @Override
  protected String[] instrumentationNames() {
    return new String[] {"elasticsearch"};
  }

  @Override
  protected String service() {
    return "elasticsearch";
  }

  @Override
  protected String component() {
    return "elasticsearch-java";
  }

  @Override
  protected String spanType() {
    return DDSpanTypes.ELASTICSEARCH;
  }

  @Override
  protected String dbType() {
    return "elasticsearch";
  }

  @Override
  protected String dbUser(final Object o) {
    return null;
  }

  @Override
  protected String dbInstance(final Object o) {
    return null;
  }

  public AgentSpan onRequest(final AgentSpan span, final Class action, final Class request) {
    if (action != null) {
      String simpleName = action.getSimpleName();
      span.setSpanName(simpleName);
      span.setTag(DDTags.RESOURCE_NAME, simpleName);
      span.setTag("elasticsearch.action", simpleName);
    }
    if (request != null) {
      span.setTag("elasticsearch.request", request.getSimpleName());
    }
    return span;
  }
}
