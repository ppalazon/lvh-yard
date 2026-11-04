package dev.palazon.lvhyard.dashboard.profiles;

import java.util.Map;
import java.util.Set;

/**
 * A Route Profile as its Home Workstation's Consul KV holds it.
 *
 * @param middlewares in Traefik's {@code http.middlewares} shape: the profile and every chain
 *     member it reaches, by their names in the file
 */
public record RouteProfileDefinition(String name, Map<String, Map<String, Object>> middlewares) {

  /** The middleware types that ask who the user is. */
  private static final Set<String> AUTH_TYPES = Set.of("basicAuth", "digestAuth", "forwardAuth");

  public RouteProfileDefinition {
    middlewares = Map.copyOf(middlewares);
  }

  /** Whether it, or a chain member it reaches, asks who the user is. */
  public boolean asksWhoTheUserIs() {
    return middlewares.values().stream()
        .flatMap(middleware -> middleware.keySet().stream())
        .anyMatch(AUTH_TYPES::contains);
  }
}
