package cc.lvhy.yard.dashboard.profiles;

import cc.lvhy.yard.dashboard.consul.Consul;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * The Route Profiles that a Workstation's Consul KV holds, at {@link #KEY_PREFIX}{@code <name>}.
 * Every Workstation that routes a Publication naming one reads it there (ADR-0004). The value is
 * only the profile's own middleware in Traefik's format, such as {@code {"compress": {}}}. A chain
 * names its members, as Traefik does. {@link RouteProfileEditor} writes them.
 */
@Component
public class RouteProfiles {

  private static final String KEY_PREFIX = "lvh-yard/route-profiles/";

  private static final ObjectMapper JSON = JsonMapper.builder().build();

  private static final TypeReference<Map<String, Object>> STORED = new TypeReference<>() {};

  private final Consul consul;

  RouteProfiles(Consul consul) {
    this.consul = consul;
  }

  /** The names of this Workstation's profiles, which its Publications can name. */
  public List<String> names() {
    return List.copyOf(stored(null).keySet());
  }

  /**
   * The profiles a Workstation's Consul KV holds, by name, each with the chain members it reaches.
   * A member that Consul KV lacks is left out, but its chain still names it, so Traefik refuses the
   * Route rather than open it.
   *
   * @param datacenter the Workstation's; {@code null} for this Workstation
   */
  public Map<String, RouteProfileDefinition> stored(String datacenter) {
    Map<String, Map<String, Object>> own = new TreeMap<>();
    consul
        .values(datacenter, KEY_PREFIX)
        .forEach((key, value) ->
            own.put(key.substring(KEY_PREFIX.length()), JSON.readValue(value, STORED)));
    Map<String, RouteProfileDefinition> stored = new TreeMap<>();
    own.keySet()
        .forEach(name -> stored.put(name, new RouteProfileDefinition(name, reached(name, own))));
    return stored;
  }

  /** The profile and every chain member it reaches that {@code own} holds, the profile first. */
  private static Map<String, Map<String, Object>> reached(
      String name, Map<String, Map<String, Object>> own) {
    Map<String, Map<String, Object>> reached = new LinkedHashMap<>();
    reach(name, own, reached);
    return reached;
  }

  private static void reach(
      String name, Map<String, Map<String, Object>> own, Map<String, Map<String, Object>> reached) {
    if (reached.containsKey(name) || !own.containsKey(name)) {
      return;
    }
    reached.put(name, own.get(name));
    RouteProfileChecks.chainMembers(own.get(name)).forEach(member -> reach(member, own, reached));
  }

  /** The Consul KV key of the profile. */
  static String key(String name) {
    return KEY_PREFIX + name;
  }

  /** The Consul KV value of a profile: its own middleware. */
  static String value(Map<String, ?> middleware) {
    return JSON.writeValueAsString(middleware);
  }
}
