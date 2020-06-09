// Modified by SignalFx
package datadog.trace.api;

import java.math.BigInteger;

/** Conversions between DataDog decimal ids and Zipkin hex ids. Supports both 64 and 128 bit ids. */
public class Ids {
  private static final char[] pad = "00000000000000000000000000000000".toCharArray();
  private static final char[] HexChars = {
    '0', '1', '2', '3',
    '4', '5', '6', '7',
    '8', '9', 'a', 'b',
    'c', 'd', 'e', 'f',
  };
  private static final BigInteger maxLong = new BigInteger(String.valueOf(Long.MAX_VALUE));

  public static char[] idToHexChars(final BigInteger id) {
    if (id.compareTo(maxLong) <= 0) {
      try {
        long val = id.longValue();
        char[] answer = new char[16];
        for (int i = 0; i < 16; i++) {
          long mask = (0x0FL << (i * 4));
          long nybble = (val & mask) >> (i * 4);

          answer[15 - i] = HexChars[(int) (nybble & 0xFF)];
        }
        return answer;
      } catch (NumberFormatException thatsFine) {
        // continue on to using BigInteger below
      }
    }
    final String asHex = id.toString(16);
    final int desiredLength = asHex.length() > 16 ? 32 : 16;
    final int padLength = desiredLength - asHex.length();
    final char[] s = new char[desiredLength];
    System.arraycopy(pad, 0, s, 0, desiredLength - asHex.length());
    asHex.getChars(0, asHex.length(), s, desiredLength - asHex.length());
    return s;
  }

  public static char[] idToHexChars(final String id) {
    if (id.length() <= 20) {
      // *likely* (but not definitely) a 64-bit id: try to parse as such
      try {
        long val = Long.parseLong(id);
        char[] answer = new char[16];
        for (int i = 0; i < 16; i++) {
          long mask = (0x0FL << (i * 4));
          long nybble = (val & mask) >> (i * 4);

          answer[15 - i] = HexChars[(int) (nybble & 0xFF)];
        }
        return answer;
      } catch (NumberFormatException thatsFine) {
        // continue on to using BigInteger below
      }
    }
    final String asHex = new BigInteger(id, 10).toString(16);
    final int desiredLength = asHex.length() > 16 ? 32 : 16;
    final int padLength = desiredLength - asHex.length();
    final char[] s = new char[desiredLength];
    System.arraycopy(pad, 0, s, 0, desiredLength - asHex.length());
    asHex.getChars(0, asHex.length(), s, desiredLength - asHex.length());
    return s;
  }

  public static String idToHex(final String id) {
    return new String(idToHexChars(id));
  }

  /** The inverse of idToHex. Returns a string that is used as an id in DDSpan. */
  public static String hexToId(String hex) {
    return new BigInteger(hex, 16).toString();
  }
}
