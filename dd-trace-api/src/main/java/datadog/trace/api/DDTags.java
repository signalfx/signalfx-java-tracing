// Modified by SignalFx
package datadog.trace.api;

public class DDTags {
  public static final String SPAN_TYPE = "span.type";
  public static final String SERVICE_NAME = "service.name";
  public static final String RESOURCE_NAME = "resource.name";
  public static final String THREAD_NAME = "thread.name";
  public static final String THREAD_ID = "thread.id";
  public static final String DB_STATEMENT = "db.statement";

  public static final String HTTP_QUERY = "http.query.string";
  public static final String HTTP_FRAGMENT = "http.fragment.string";

  public static final String USER_NAME = "user.principal";
  public static final String ENTITY_NAME = "entity.name";

  public static final String ERROR_MSG =
      "sfx.error.message"; // string representing the error message
  public static final String ERROR_TYPE =
      "sfx.error.object"; // string representing the type of the error
  public static final String ERROR_STACK = "sfx.error.stack"; // human readable version of the stack

  public static final String ANALYTICS_SAMPLE_RATE = "_dd1.sr.eausr";
  @Deprecated public static final String EVENT_SAMPLE_RATE = ANALYTICS_SAMPLE_RATE;

  /** Manually force tracer to be keep the trace */
  public static final String MANUAL_KEEP = "manual.keep";
  /** Manually force tracer to be drop the trace */
  public static final String MANUAL_DROP = "manual.drop";

  // Used by ZipkinV2Api to prevent OT/instrumentation api dep
  public static final String SPAN_KIND = "span.kind";
  public static final String SPAN_KIND_SERVER = "SERVER";
  public static final String SPAN_KIND_CLIENT = "CLIENT";
  public static final String SPAN_KIND_PRODUCER = "PRODUCER";
  public static final String SPAN_KIND_CONSUMER = "CONSUMER";
}
