package dev.palazon.lvhyard.dashboard.profiles;

import dev.palazon.lvhyard.dashboard.services.OutOfRange;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SequencedSet;
import java.util.Set;
import java.util.regex.Pattern;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

/**
 * Reads the text of {@code route-profiles.yaml} and checks each profile in it. A profile must work
 * on every Workstation that routes it (ADR-0004), so it may not read a file, use a plugin, send
 * {@code forwardAuth} to an address only this Workstation reaches, or chain a middleware from
 * outside the file.
 */
final class RouteProfileReader {

  /**
   * Traefik's own HTTP middlewares. Not {@code errors}: it names a Traefik service, and LVH Yard
   * gives Traefik only its own.
   */
  private static final Set<String> TYPES =
      Set.of(
          "addPrefix",
          "basicAuth",
          "buffering",
          "chain",
          "circuitBreaker",
          "compress",
          "contentType",
          "digestAuth",
          "forwardAuth",
          "grpcWeb",
          "headers",
          "inFlightReq",
          "ipAllowList",
          "ipWhiteList",
          "passTLSClientCert",
          "rateLimit",
          "redirectRegex",
          "redirectScheme",
          "replacePath",
          "replacePathRegex",
          "retry",
          "stripPrefix",
          "stripPrefixRegex");

  /** A Consul key part, and Traefik's {@code @} would name another provider. */
  private static final Pattern NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]*");

  private RouteProfileReader() {}

  /**
   * @param error why the whole file cannot be read, or {@code null}
   * @param definitions each valid profile's value for Consul: the profile and every chain member it
   *     reaches, by their names in the file, in Traefik's {@code http.middlewares} shape
   */
  record Reading(
      String error, List<RouteProfile> profiles, Map<String, Map<String, Object>> definitions) {

    static Reading broken(String error) {
      return new Reading(error, List.of(), Map.of());
    }
  }

  static Reading read(String yaml, OutOfRange outOfRange) {
    Object root;
    try {
      root = new Yaml(new SafeConstructor(new LoaderOptions())).load(yaml);
    } catch (YAMLException e) {
      return Reading.broken("route-profiles.yaml is not valid YAML: " + e.getMessage());
    }
    Map<String, Object> middlewares;
    try {
      middlewares = middlewares(root);
    } catch (IllegalArgumentException e) {
      return Reading.broken(e.getMessage());
    }
    Checker checker = new Checker(middlewares, outOfRange);
    List<RouteProfile> profiles = new ArrayList<>();
    Map<String, Map<String, Object>> definitions = new LinkedHashMap<>();
    middlewares.forEach((name, middleware) -> {
      String error = checker.refusal(name);
      profiles.add(new RouteProfile(name, type(middleware), error));
      if (error == null) {
        Map<String, Object> definition = new LinkedHashMap<>();
        for (String member : checker.reached(name)) {
          definition.put(member, middlewares.get(member));
        }
        definitions.put(name, definition);
      }
    });
    return new Reading(null, profiles, definitions);
  }

  /** The middlewares under {@code http.middlewares}, by name. An empty file has none. */
  private static Map<String, Object> middlewares(Object root) {
    if (root == null) {
      return Map.of();
    }
    Map<String, Object> top = mapOrRefuse(root, "route-profiles.yaml must hold http.middlewares.");
    for (String key : top.keySet()) {
      if (!key.equals("http")) {
        throw new IllegalArgumentException(
            "route-profiles.yaml may hold only http.middlewares, not " + key + ".");
      }
    }
    if (top.get("http") == null) {
      return Map.of();
    }
    Map<String, Object> http =
        mapOrRefuse(top.get("http"), "route-profiles.yaml must hold http.middlewares.");
    for (String key : http.keySet()) {
      if (!key.equals("middlewares")) {
        throw new IllegalArgumentException(
            "route-profiles.yaml may hold only http.middlewares, not http." + key + ".");
      }
    }
    if (http.get("middlewares") == null) {
      return Map.of();
    }
    Map<String, Object> middlewares =
        mapOrRefuse(
            http.get("middlewares"),
            "http.middlewares in route-profiles.yaml must map each profile's name to its"
                + " middleware.");
    Map<String, Object> copy = new LinkedHashMap<>();
    middlewares.forEach(
        (name, middleware) -> copy.put(String.valueOf(name), withEmptyOptions(middleware)));
    return copy;
  }

  /** {@code compress:} with nothing after it is {@code compress: {}} for Traefik. */
  private static Object withEmptyOptions(Object middleware) {
    if (!(middleware instanceof Map<?, ?> map)) {
      return middleware;
    }
    Map<Object, Object> copy = new LinkedHashMap<>();
    map.forEach((type, options) -> copy.put(type, options == null ? Map.of() : options));
    return copy;
  }

  /** The type of a middleware with exactly one, else {@code null}. */
  private static String type(Object middleware) {
    return middleware instanceof Map<?, ?> map && map.size() == 1
        ? String.valueOf(map.keySet().iterator().next())
        : null;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> mapOrRefuse(Object value, String error) {
    if (value instanceof Map<?, ?> map) {
      return (Map<String, Object>) map;
    }
    throw new IllegalArgumentException(error);
  }

  private static final class Checker {

    private final Map<String, Object> middlewares;

    private final OutOfRange outOfRange;

    Checker(Map<String, Object> middlewares, OutOfRange outOfRange) {
      this.middlewares = middlewares;
      this.outOfRange = outOfRange;
    }

    /** Why the profile is refused, or {@code null}. */
    String refusal(String name) {
      return refusal(name, new LinkedHashSet<>());
    }

    /** The profile and every chain member it reaches, the profile first. It must be valid. */
    SequencedSet<String> reached(String name) {
      SequencedSet<String> reached = new LinkedHashSet<>();
      reach(name, reached);
      return reached;
    }

    private void reach(String name, SequencedSet<String> reached) {
      if (reached.add(name)) {
        members(name).forEach(member -> reach(member, reached));
      }
    }

    /** {@code chain} is the chain members being checked, so a loop is found. */
    private String refusal(String name, SequencedSet<String> chain) {
      if (!NAME.matcher(name).matches()) {
        return name + " is not a valid name. Use letters, digits, - and _.";
      }
      String type = type(middlewares.get(name));
      if (type == null) {
        return name + " must have exactly one middleware type, such as headers or basicAuth.";
      }
      if (type.equals("plugin")) {
        return name + " uses a plugin. Another Workstation's Traefik may not have it.";
      }
      if (!TYPES.contains(type)) {
        return name + " has the type " + type + ", which is not a Traefik middleware.";
      }
      Object options = ((Map<?, ?>) middlewares.get(name)).get(type);
      if (!(options instanceof Map<?, ?> settings)) {
        return name + " must give its " + type + " settings as a map.";
      }
      return switch (type) {
        case "basicAuth", "digestAuth" ->
            settings.containsKey("usersFile")
                ? name
                    + " reads its users from usersFile, a file that other Workstations do not"
                    + " have. Write them under users instead, as hashes from htpasswd."
                : null;
        case "forwardAuth" -> forwardAuthRefusal(name, settings.get("address"));
        case "chain" -> chainRefusal(name, settings.get("middlewares"), chain);
        default -> null;
      };
    }

    private String forwardAuthRefusal(String name, Object address) {
      String host;
      try {
        host = address instanceof String text ? new URI(text).getHost() : null;
      } catch (URISyntaxException e) {
        host = null;
      }
      if (host == null) {
        return name + " must give forwardAuth an address such as http://auth.example:4181/auth.";
      }
      host = host.startsWith("[") ? host.substring(1, host.length() - 1) : host;
      String lower = host.toLowerCase(Locale.ROOT);
      if (lower.equals("localhost") || lower.endsWith(".localhost") || outOfRange.contains(host)) {
        return name
            + " sends forwardAuth to "
            + host
            + ", which only this Workstation reaches. Use an address that every Workstation"
            + " reaches.";
      }
      return null;
    }

    private String chainRefusal(String name, Object members, SequencedSet<String> chain) {
      if (!(members instanceof List<?> list)
          || !list.stream().allMatch(member -> member instanceof String)) {
        return name + " must list the names of its chain's middlewares.";
      }
      chain.add(name);
      try {
        for (Object member : list) {
          String memberName = (String) member;
          if (!middlewares.containsKey(memberName)) {
            return name + " names " + memberName + ", which is not in this file.";
          }
          if (chain.contains(memberName)) {
            return name + " is a chain that comes back to " + memberName + ".";
          }
          if (refusal(memberName, chain) != null) {
            return name + " names " + memberName + ", which is refused.";
          }
        }
        return null;
      } finally {
        chain.remove(name);
      }
    }

    private List<String> members(String name) {
      Object middleware = middlewares.get(name);
      if (!"chain".equals(type(middleware))) {
        return List.of();
      }
      Object chain = ((Map<?, ?>) middleware).get("chain");
      return ((List<?>) ((Map<?, ?>) chain).get("middlewares"))
          .stream().map(String.class::cast).toList();
    }
  }
}
