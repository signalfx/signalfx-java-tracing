// Modified by SignalFx
package datadog.trace.api;

import java.math.BigInteger;

/** Conversions between DataDog numeric ids and Zipkin hex ids. Supports both 64 and 128 bit ids. */
public class Ids {
  private static char[] pad = "00000000000000000000000000000000".toCharArray();

  public static String idToHex(String id) {
    final String asHex = new BigInteger(id, 10).toString(16);
    final int desiredLength = asHex.length() > 16 ? 32 : 16;
    final int padLength = desiredLength - asHex.length();
    StringBuilder sb = new StringBuilder(desiredLength);
    sb.insert(0, pad, 0, padLength);
    sb.insert(padLength, asHex);
    return sb.toString();
  }

  /** The inverse of idToHex. Returns a string that is used as an id in DDSpan. */
  public static String hexToId(String hex) {
    return new BigInteger(hex, 16).toString();
  }
}
