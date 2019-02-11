package datadog.trace.common.util;

import java.math.BigInteger;

/** Conversions between DataDog numeric ids and Zipkin hex ids. Supports both 64 and 128 bit ids. */
public class Ids {
  private static final String ID_8_BYTES = "%016x";
  private static final String ID_16_BYTES = "%032x";

  // Anything bigger than this will require more than 16 hex digits to represent
  private static final BigInteger BIGINT_UNSIGNED_LONG_MAX =
      BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.valueOf(2)).add(BigInteger.ONE);

  public static String idToHex(String id) {
    BigInteger asInt = new BigInteger(id, 10);

    String formatStr = ID_8_BYTES;
    if (asInt.compareTo(BIGINT_UNSIGNED_LONG_MAX) > 0) {
      formatStr = ID_16_BYTES;
    }

    return String.format(formatStr, asInt);
  }

  /** The inverse of idToHex. Returns a string that is used as an id in DDSpan. */
  public static String hexToId(String hex) {
    return new BigInteger(hex, 16).toString();
  }
}
