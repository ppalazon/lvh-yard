package cc.lvhy.yard.dashboard.profiles;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A Route Profile as its Home Workstation's Consul KV holds it, with the chain members it reaches.
 *
 * @param middlewares the profile and its chain members, by their names in Consul KV, with each
 *     middleware or a reserved option, clientCertificate or mutualTLS
 */
public record RouteProfileDefinition(String name, Map<String, Map<String, Object>> middlewares) {

  /** The middleware types that ask who the user is. */
  private static final Set<String> AUTH_TYPES = Set.of("basicAuth", "digestAuth", "forwardAuth");

  public RouteProfileDefinition {
    middlewares = Map.copyOf(middlewares);
  }

  /** The profile's own options, as its Consul KV value holds them. */
  public Map<String, Object> own() {
    return middlewares.get(name);
  }

  /** Whether it, or a chain member it reaches, requires a Client Certificate. */
  public boolean requiresClientCertificate() {
    return reaches("clientCertificate");
  }

  /** Whether it, or a chain member it reaches, requires mutual TLS to the Instances. */
  public boolean requiresMutualTls() {
    return reaches("mutualTLS");
  }

  private boolean reaches(String option) {
    return middlewares.values().stream().anyMatch(value -> value.containsKey(option));
  }

  /** Leaves the reserved options out of Traefik's middleware list, including empty chains. */
  @SuppressWarnings("unchecked")
  public Map<String, Map<String, Object>> traefikMiddlewares() {
    if (!requiresClientCertificate() && !requiresMutualTls()) {
      return middlewares;
    }
    Set<String> omitted = new HashSet<>();
    middlewares.forEach((profile, value) -> {
      if (value.containsKey("clientCertificate") || value.containsKey("mutualTLS")) {
        omitted.add(profile);
      }
    });
    boolean changed;
    do {
      changed = false;
      for (var entry : middlewares.entrySet()) {
        List<String> members = RouteProfileChecks.chainMembers(entry.getValue());
        if (!members.isEmpty() && omitted.containsAll(members)) {
          changed |= omitted.add(entry.getKey());
        }
      }
    } while (changed);
    Map<String, Map<String, Object>> result = new LinkedHashMap<>();
    middlewares.forEach((profile, value) -> {
      if (!omitted.contains(profile)) {
        if (value.get("chain") instanceof Map<?, ?> chain) {
          Map<String, Object> options = new LinkedHashMap<>((Map<String, Object>) chain);
          options.put(
              "middlewares",
              RouteProfileChecks.chainMembers(value).stream()
                  .filter(member -> !omitted.contains(member))
                  .toList());
          result.put(profile, Map.of("chain", options));
        } else {
          result.put(profile, value);
        }
      }
    });
    return result;
  }

  /** Whether it, or a chain member it reaches, asks who the user is. */
  public boolean asksWhoTheUserIs() {
    return middlewares.values().stream()
        .flatMap(middleware -> middleware.keySet().stream())
        .anyMatch(type -> asksWhoTheUserIs(type));
  }

  /** Whether a middleware type, or the reserved option clientCertificate, asks who the user is. */
  public static boolean asksWhoTheUserIs(String type) {
    return AUTH_TYPES.contains(type) || type.equals("clientCertificate");
  }
}
