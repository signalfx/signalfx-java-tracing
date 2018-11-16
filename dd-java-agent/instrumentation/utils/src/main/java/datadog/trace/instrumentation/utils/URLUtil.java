package datadog.trace.instrumentation.utils;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Pattern;

/**
 * This is a rip of the DD class datadog.opentracing.decorators.URLAsResourceName to pull out the
 * core logic for determining a useful operation name for http requests/responses.
 */
public class URLUtil {
  // Matches everything after the ? character.
  public static final Pattern QUERYSTRING = Pattern.compile("\\?.*$");
  // Matches any path segments with numbers in them. (exception for versioning: "/v1/")
  public static final Pattern PATH_MIXED_ALPHANUMERICS =
      Pattern.compile("(?<=/)(?![vV]\\d{1,2}/)(?:[^\\/\\d\\?]*[\\d]+[^\\/\\?]*)");

  public static String deriveOperationName(String method, String url) {
    return deriveOperationName(method, url, true);
  }

  public static String deriveOperationName(String method, String url, boolean includeHost) {
    try {
      return deriveOperationName(method, new java.net.URL(url), includeHost);
    } catch (final MalformedURLException e) {
      return method + " " + url;
    }
  }

  public static String deriveOperationName(String method, URL url) {
    return deriveOperationName(method, url, true);
  }

  public static String deriveOperationName(String method, URL url, boolean includeHost) {
    String path = norm(url.getPath());
    return method + " " + (includeHost ? url.getHost() : "") + path;
  }

  // Method to normalise the url string
  private static String norm(final String origin) {

    String norm = origin;
    norm = QUERYSTRING.matcher(norm).replaceAll("");
    norm = PATH_MIXED_ALPHANUMERICS.matcher(norm).replaceAll("?");

    if (norm.trim().isEmpty()) {
      norm = "/";
    }

    return norm;
  }
}
