package datadog.trace.common.util;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Pattern;

/**
 * This is a rip of the DD class datadog.opentracing.decorators.URLAsResourceName to pull out the
 * core logic for determining the operation name for http requests/responses.
 */
public class URLUtil {
  // Matches everything after the ? character.
  public static final Pattern QUERYSTRING = Pattern.compile("\\?.*$");
  // Matches any path segments with numbers in them. (exception for versioning: "/v1/")
  public static final Pattern PATH_MIXED_ALPHANUMERICS =
    Pattern.compile("(?<=/)(?![vV]\\d{1,2}/)(?:[^\\/\\d\\?]*[\\d]+[^\\/\\?]*)");

  public static String deriveOperationName(String method, String url) {
    try {
      return deriveOperationName(method, new java.net.URL(url));
    } catch (final MalformedURLException e) {
      return method + " " + url;
    }
  }

  public static String deriveOperationName(String method, URL url) {
    return method + " " + deriveHostPath(url);
  }

  public static String deriveHostPath(URL url) {
    String path = norm(url.getPath());
    return url.getHost() + path;
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
