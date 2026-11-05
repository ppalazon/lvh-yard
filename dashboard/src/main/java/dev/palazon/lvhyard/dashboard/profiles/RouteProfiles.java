package dev.palazon.lvhyard.dashboard.profiles;

import dev.palazon.lvhyard.dashboard.consul.Consul;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * The Route Profiles that a Workstation's Consul KV holds, at {@link #KEY_PREFIX}{@code <name>}.
 * Every Workstation that routes a Publication naming one reads it there (ADR-0004). The value is a
 * JSON object in Traefik's {@code http.middlewares} shape: the profile and every chain member it
 * reaches. {@link RouteProfileEditor} writes them.
 */
@Component
public class RouteProfiles {

  private static final String KEY_PREFIX = "lvh-yard/route-profiles/";

  private static final ObjectMapper JSON = JsonMapper.builder().build();

  private static final TypeReference<Map<String, Map<String, Object>>> STORED =
      new TypeReference<>() {};

  private final Consul consul;

  RouteProfiles(Consul consul) {
    this.consul = consul;
  }

  /** The names of this Workstation's profiles, which its Publications can name. */
  public List<String> names() {
    return List.copyOf(stored(null).keySet());
  }

  /**
   * The profiles a Workstation's Consul KV holds, by name.
   *
   * @param datacenter the Workstation's; {@code null} for this Workstation
   */
  public Map<String, RouteProfileDefinition> stored(String datacenter) {
    Map<String, RouteProfileDefinition> stored = new TreeMap<>();
    consul
        .values(datacenter, KEY_PREFIX)
        .forEach((key, value) -> {
          String name = key.substring(KEY_PREFIX.length());
          stored.put(name, new RouteProfileDefinition(name, JSON.readValue(value, STORED)));
        });
    return stored;
  }

  /** The Consul KV key of the profile. */
  static String key(String name) {
    return KEY_PREFIX + name;
  }

  /**
   * The Consul KV value of a profile.
   *
   * @param middlewares the profile and every chain member it reaches
   */
  static String value(Map<String, ?> middlewares) {
    return JSON.writeValueAsString(middlewares);
  }
}
