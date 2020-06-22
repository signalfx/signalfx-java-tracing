package datadog.trace.bootstrap.instrumentation;

import datadog.trace.api.Ids;
import io.opentracing.SpanContext;
import java.math.BigInteger;
import java.util.Arrays;

public class TraceParentHeaderFormatter {

  public static char[] padTo128(final char[] id) {
    if (id.length == 32) {
      return id;
    }
    char[] answer = new char[32];
    Arrays.fill(answer, '0');
    System.arraycopy(id, 0, answer, 32 - id.length, id.length);
    return answer;
  }

  public static String format(final SpanContext context) {
    // https://www.w3.org/TR/server-timing/
    // https://www.w3.org/TR/trace-context/#traceparent-header
    StringBuffer traceParent = new StringBuffer();
    // FIXME Allocations/transformations here are unnecessary given better internal interfaces
    String traceIdDec = context.toTraceId();
    String spanIdDec = context.toSpanId();

    traceParent.append("traceparent;desc=\"00-");
    traceParent.append(padTo128(Ids.idToHexChars(new BigInteger(traceIdDec))));
    traceParent.append("-");
    traceParent.append(Ids.idToHexChars(new BigInteger(spanIdDec)));
    traceParent.append("-01\"");
    return traceParent.toString();
  }
}
