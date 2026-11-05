package dev.palazon.lvhyard.dashboard.profiles;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

/**
 * Reads the text of {@code route-profiles.yaml}, and checks its profiles with {@link
 * RouteProfileChecks}. The file holds all profiles, so a chain may name only profiles in it.
 */
final class RouteProfileReader {

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

  static Reading read(String yaml, RouteProfileChecks checks) {
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
    RouteProfileChecks.Checked checked = checks.check(middlewares, Map.of());
    Map<String, String> errors = new LinkedHashMap<>();
    for (RouteProfileChecks.Problem problem : checked.problems()) {
      // An unknown type is most likely a typo in a profile that Services already use, so it
      // breaks the whole file and the Consul KV keeps the profiles read before.
      if (problem.unknownType()) {
        return Reading.broken("route-profiles.yaml: " + problem.reason());
      }
      errors.put(problem.profile(), problem.reason());
    }
    List<RouteProfile> profiles = new ArrayList<>();
    middlewares.forEach((name, middleware) ->
        profiles.add(
            new RouteProfile(name, RouteProfileChecks.type(middleware), errors.get(name))));
    return new Reading(null, profiles, checked.definitions());
  }

  /** The middlewares under {@code http.middlewares}, by name. An empty file has none. */
  private static Map<String, Object> middlewares(Object root) {
    if (root == null) {
      return Map.of();
    }
    Map<String, Object> top = mapOrRefuse(root, "route-profiles.yaml must hold http.middlewares.");
    checkOnlyKey(top, "http", "");
    if (top.get("http") == null) {
      return Map.of();
    }
    Map<String, Object> http =
        mapOrRefuse(top.get("http"), "route-profiles.yaml must hold http.middlewares.");
    checkOnlyKey(http, "middlewares", "http.");
    if (http.get("middlewares") == null) {
      return Map.of();
    }
    Map<String, Object> middlewares =
        mapOrRefuse(
            http.get("middlewares"),
            "http.middlewares in route-profiles.yaml must map each profile's name to its"
                + " middleware.");
    Map<String, Object> copy = new LinkedHashMap<>();
    middlewares.forEach((name, middleware) -> copy.put(String.valueOf(name), middleware));
    return copy;
  }

  /** {@code path} is where {@code map} is in the file, such as {@code http.}. */
  private static void checkOnlyKey(Map<String, Object> map, String allowed, String path) {
    for (String key : map.keySet()) {
      if (!key.equals(allowed)) {
        throw new IllegalArgumentException(
            "route-profiles.yaml may hold only http.middlewares, not " + path + key + ".");
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> mapOrRefuse(Object value, String error) {
    if (value instanceof Map<?, ?> map) {
      return (Map<String, Object>) map;
    }
    throw new IllegalArgumentException(error);
  }
}
