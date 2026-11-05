package dev.palazon.lvhyard.dashboard.profiles;

import java.util.Map;
import java.util.Set;

/**
 * A Route Profile as its Home Workstation's Consul KV holds it, with the chain members it reaches.
 *
 * @param middlewares in Traefik's {@code http.middlewares} shape: the profile and every chain
 *     member it reaches that Consul KV holds, by their names in Consul KV
 */
public record RouteProfileDefinition(String name, Map<String, Map<String, Object>> middlewares) {

  /** The middleware types that ask who the user is. */
  private static final Set<String> AUTH_TYPES = Set.of("basicAuth", "digestAuth", "forwardAuth");

  public RouteProfileDefinition {
    middlewares = Map.copyOf(middlewares);
  }

  /** The profile's own middleware, as its Consul KV value holds it. */
  public Map<String, Object> own() {
    return middlewares.get(name);
  }

  /** Whether it, or a chain member it reaches, asks who the user is. */
  public boolean asksWhoTheUserIs() {
    return middlewares.values().stream()
        .flatMap(middleware -> middleware.keySet().stream())
        .anyMatch(AUTH_TYPES::contains);
  }
}
