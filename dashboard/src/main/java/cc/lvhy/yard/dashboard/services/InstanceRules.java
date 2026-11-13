package cc.lvhy.yard.dashboard.services;

import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The input rules of the Yard for Service names, Alias names and Static Instances. It knows no
 * Consul and no Spring, so a test needs neither.
 */
public final class InstanceRules {

  /** The longest DNS label. */
  static final int MAX_NAME_LENGTH = 63;

  /** A single DNS label: what a hostname's first part can be. */
  private static final Pattern DNS_LABEL =
      Pattern.compile("[a-z0-9]([a-z0-9-]{0," + (MAX_NAME_LENGTH - 2) + "}[a-z0-9])?");

  /** An IP address or a hostname. Nothing that would break a URL, such as /, ?, % or a space. */
  private static final Pattern HOST = Pattern.compile("[A-Za-z0-9.:-]+");

  /** Meta keys that LVH Yard keeps for itself. */
  static final String RESERVED_META = "lvh-yard-";

  /** Consul keeps its own server entry in its catalog under this name. */
  private static final String CONSUL = "consul";

  /** For the API docs of each request that names a Service or an Alias. */
  static final String INVALID_NAME = "The name is not a single DNS label or is consul";

  private static final String CONSUL_REFUSAL = "consul is Consul's own entry in its catalog";

  private static final Duration CHECK_INTERVAL = Duration.ofSeconds(10);

  private static final Duration CHECK_TIMEOUT = Duration.ofSeconds(2);

  /** The shortest interval Consul runs a check at. */
  private static final Duration SHORTEST_INTERVAL = Duration.ofSeconds(1);

  private static final Pattern DURATION = Pattern.compile("(\\d{1,9})(ms|s|m|h)");

  /** A tag: anything without a space or a comma, so tags can be typed as a comma-separated list. */
  private static final Pattern TAG = Pattern.compile("[^\\s,]{1,255}");

  private static final Pattern META_KEY = Pattern.compile("[A-Za-z0-9_-]{1,128}");

  private static final int META_VALUE_LENGTH = 512;

  /**
   * The most meta keys an Instance can have: Consul allows 64, and LVH Yard keeps a few for itself.
   */
  private static final int META_KEYS = 60;

  /** An absolute path, with nothing that would break the check's URL, such as a space or a #. */
  private static final Pattern PATH = Pattern.compile("/[A-Za-z0-9._~!$&'()*+,;=:@%/?-]*");

  private InstanceRules() {}

  /**
   * The Static Instance as it is stored, with the default host and check filled in.
   *
   * @param defaultHost asked only when {@code request} names no host
   * @throws InvalidRegistrationException at the first rule that {@code request} breaks
   */
  static StaticInstanceRequest checked(
      StaticInstanceRequest request, Supplier<String> defaultHost) {
    checkPort(request.port());
    String host =
        request.host() == null || request.host().isBlank()
            ? defaultHost.get()
            : request.host().strip();
    checkHost(host);
    HealthCheck check = check(request.check());
    checkTags(request.tags());
    checkMeta(request.meta());
    return new StaticInstanceRequest(host, request.port(), check, request.tags(), request.meta());
  }

  static void checkName(String service) {
    checkName("Service", service);
  }

  /** The DNS label rule only. The name {@value #CONSUL} passes it. */
  static boolean isDnsLabel(String name) {
    return DNS_LABEL.matcher(name).matches();
  }

  /** A Service and an Alias both take a hostname's first part. */
  public static void checkName(String kind, String name) {
    if (!isDnsLabel(name)) {
      throw new InvalidRegistrationException(
          "name",
          "The "
              + kind
              + " name "
              + name
              + " is not a single DNS label: use 1 to 63 lowercase letters, digits and -, not"
              + " starting or ending with -.");
    }
    if (name.equals(CONSUL)) {
      throw new InvalidRegistrationException("name", CONSUL_REFUSAL);
    }
  }

  private static void checkHost(String host) {
    if (!HOST.matcher(host).matches()) {
      throw new InvalidRegistrationException(
          "host",
          "The host "
              + host
              + " is not an IP address or a hostname: use letters, digits, ., - and :.");
    }
  }

  private static void checkTags(List<String> tags) {
    for (String tag : tags) {
      if (tag == null || !TAG.matcher(tag).matches()) {
        throw new InvalidRegistrationException(
            "tags",
            "The tag \""
                + tag
                + "\" is not one word: use 1 to"
                + " 255 characters without spaces or commas.");
      }
    }
  }

  private static void checkMeta(Map<String, String> meta) {
    if (meta.size() > META_KEYS) {
      throw new InvalidRegistrationException(
          "meta", "An Instance can have at most " + META_KEYS + " meta keys.");
    }
    meta.forEach((key, value) -> {
      if (!META_KEY.matcher(key).matches()) {
        throw new InvalidRegistrationException(
            "meta",
            "The meta key "
                + key
                + " is not one Consul"
                + " allows: use 1 to 128 letters, digits, - and _.");
      }
      if (key.startsWith(RESERVED_META) || key.startsWith("consul-")) {
        throw new InvalidRegistrationException(
            "meta",
            "The meta key "
                + key
                + " is kept for "
                + (key.startsWith("consul-") ? "Consul" : "LVH Yard")
                + ": use another key.");
      }
      if (value != null && value.length() > META_VALUE_LENGTH) {
        throw new InvalidRegistrationException(
            "meta",
            "The meta value of "
                + key
                + " is too long: use at"
                + " most "
                + META_VALUE_LENGTH
                + " characters.");
      }
    });
  }

  static void checkPort(Integer port) {
    checkPort("port", "A Static Instance", port);
  }

  /** {@code what} names what needs the port, for the message. */
  static void checkPort(String field, String what, Integer port) {
    if (port == null) {
      throw new InvalidRegistrationException(field, what + " needs a port.");
    }
    if (port < 1 || port > 65535) {
      throw notATcpPort(field, String.valueOf(port));
    }
  }

  private static InvalidRegistrationException notATcpPort(String field, String port) {
    return new InvalidRegistrationException(
        field, "The port " + port + " is not a TCP port: use 1 to 65535.");
  }

  public static Integer parsePort(String port) {
    return parsePort("port", port);
  }

  public static Integer parsePort(String field, String port) {
    if (port == null || port.isBlank()) {
      return null;
    }
    try {
      return Integer.valueOf(port.strip());
    } catch (NumberFormatException e) {
      throw notATcpPort(field, port);
    }
  }

  /** Separated by commas, with the spaces around them dropped. */
  public static List<String> parseTags(String tags) {
    return tags == null
        ? List.of()
        : Arrays.stream(tags.split(",")).map(String::strip).filter(tag -> !tag.isEmpty()).toList();
  }

  /** One {@code key=value} on each line, with the spaces around both dropped. */
  public static Map<String, String> parseMeta(String meta) {
    Map<String, String> values = new LinkedHashMap<>();
    if (meta == null) {
      return values;
    }
    for (String line : meta.split("\\R")) {
      if (line.isBlank()) {
        continue;
      }
      int equals = line.indexOf('=');
      if (equals < 0) {
        throw new InvalidRegistrationException(
            "meta",
            "The meta line \""
                + line.strip()
                + "\" is not key=value: write one key=value on each line.");
      }
      values.put(line.substring(0, equals).strip(), line.substring(equals + 1).strip());
    }
    return values;
  }

  /** With no check at all, the Instance gets a TCP check on its port. */
  static HealthCheck check(HealthCheck check) {
    if (check == null) {
      check = new HealthCheck(null, null, null, null);
    }
    String type =
        check.type() != null
            ? check.type()
            : check.path() != null ? HealthCheck.HTTP : HealthCheck.TCP;
    if (type.equals("script")) {
      throw new InvalidRegistrationException(
          "check", "Script checks are not offered: use an http or a tcp" + " check.");
    }
    if (!type.equals(HealthCheck.HTTP) && !type.equals(HealthCheck.TCP)) {
      throw new InvalidRegistrationException(
          "check", "The check " + type + " is not one LVH Yard offers:" + " use http or tcp.");
    }
    String path = null;
    if (type.equals(HealthCheck.HTTP)) {
      path = check.path() == null || check.path().isBlank() ? "/" : check.path();
      if (!PATH.matcher(path).matches()) {
        throw new InvalidRegistrationException(
            "path",
            "The path "
                + path
                + " is not one an HTTP check"
                + " can GET: start it with / and leave out spaces and #.");
      }
    } else if (check.path() != null && !check.path().isBlank()) {
      throw new InvalidRegistrationException(
          "path", "A TCP check has no path: use an http check to GET " + check.path() + ".");
    }
    Duration interval = duration("interval", check.interval(), CHECK_INTERVAL);
    if (interval.compareTo(SHORTEST_INTERVAL) < 0) {
      throw new InvalidRegistrationException(
          "interval",
          "The interval "
              + check.interval()
              + " is too short: a check runs at most once a second.");
    }
    Duration timeout = duration("timeout", check.timeout(), CHECK_TIMEOUT);
    return new HealthCheck(type, path, HealthCheck.format(interval), HealthCheck.format(timeout));
  }

  /** Zero is refused. */
  private static Duration duration(String field, String text, Duration otherwise) {
    if (text == null || text.isBlank()) {
      return otherwise;
    }
    Matcher matcher = DURATION.matcher(text.strip());
    if (!matcher.matches() || Long.parseLong(matcher.group(1)) == 0) {
      throw new InvalidRegistrationException(
          field,
          "The "
              + field
              + " "
              + text
              + " is not a duration: use"
              + " a whole number above 0 and ms, s, m or h, such as 10s.");
    }
    long amount = Long.parseLong(matcher.group(1));
    return switch (matcher.group(2)) {
      case "ms" -> Duration.ofMillis(amount);
      case "s" -> Duration.ofSeconds(amount);
      case "m" -> Duration.ofMinutes(amount);
      default -> Duration.ofHours(amount);
    };
  }
}
