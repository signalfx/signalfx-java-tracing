package datadog.trace.bootstrap.instrumentation;

import datadog.trace.api.Ids;
import io.opentracing.SpanContext;
import java.math.BigInteger;

public class TraceParentHeaderFormatter {

  public static String format(SpanContext context) {
    StringBuffer traceParent = new StringBuffer();
    // FIXME Allocations/transformations here are unnecessary given better internal interfaces
    String traceIdDec = context.toTraceId();
    String spanIdDec = context.toSpanId();

    traceParent.append("traceparent;desc=\"00-");
    traceParent.append(Ids.idToHexChars(new BigInteger(traceIdDec)));
    traceParent.append("-");
    traceParent.append(Ids.idToHexChars(new BigInteger(spanIdDec)));
    traceParent.append("-01\"");
    return traceParent.toString();
  }
}
