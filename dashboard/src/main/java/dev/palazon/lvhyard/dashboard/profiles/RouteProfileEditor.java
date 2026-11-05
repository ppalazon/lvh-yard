package dev.palazon.lvhyard.dashboard.profiles;

import dev.palazon.lvhyard.dashboard.consul.Consul;
import dev.palazon.lvhyard.dashboard.services.Publications;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Component;

/**
 * Lists, adds, replaces and removes this Workstation's Route Profiles in its Consul KV. Every write
 * path uses it: the API, the page, and the CLI through the API.
 */
@Component
public class RouteProfileEditor {

  private final RouteProfiles profiles;

  private final RouteProfileChecks checks;

  private final Publications publications;

  private final Consul consul;

  RouteProfileEditor(
      RouteProfiles profiles, RouteProfileChecks checks, Publications publications, Consul consul) {
    this.profiles = profiles;
    this.checks = checks;
    this.publications = publications;
    this.consul = consul;
  }

  /** By name. */
  public List<RouteProfile> list() {
    Map<String, List<String>> services = publications.servicesByProfile();
    return profiles.stored(null).values().stream()
        .map(definition ->
            new RouteProfile(
                definition.name(),
                RouteProfileChecks.type(own(definition)),
                services.getOrDefault(definition.name(), List.of())))
        .toList();
  }

  /**
   * @throws NoRouteProfileException when there is none of this name
   */
  public RouteProfileDetail find(String name) {
    RouteProfileDefinition definition = profiles.stored(null).get(name);
    if (definition == null) {
      throw new NoRouteProfileException(name);
    }
    Map<String, Object> own = own(definition);
    return new RouteProfileDetail(
        name,
        RouteProfileChecks.type(own),
        publications.servicesByProfile().getOrDefault(name, List.of()),
        own);
  }

  /**
   * Adds or replaces each profile of {@code set}, and keeps the profiles it does not name. A chain
   * may name a profile of the set or one already stored.
   *
   * @param set each profile's middleware in Traefik's format, by name
   * @throws RefusedRouteProfilesException when one profile is refused; then nothing is written
   */
  public synchronized void save(Map<String, ?> set) {
    Map<String, RouteProfileDefinition> stored = profiles.stored(null);
    RouteProfileChecks.Checked checked = checks.check(set, stored);
    if (!checked.problems().isEmpty()) {
      throw new RefusedRouteProfilesException(checked.problems());
    }
    Map<String, RouteProfileDefinition> after = new TreeMap<>(stored);
    checked
        .definitions()
        .forEach((name, middlewares) ->
            after.put(name, new RouteProfileDefinition(name, asMiddlewares(middlewares))));
    // A stored chain's value holds copies of its members, so a chain that reaches a changed
    // profile is written again with the new copy.
    Map<String, Object> chains = new LinkedHashMap<>();
    stored.forEach((name, definition) -> {
      Map<String, Object> own = own(definition);
      if (!set.containsKey(name) && "chain".equals(RouteProfileChecks.type(own))) {
        chains.put(name, own);
      }
    });
    Map<String, Map<String, Object>> written = new LinkedHashMap<>(checked.definitions());
    written.putAll(checks.check(chains, after).definitions());
    Map<String, String> values = new LinkedHashMap<>();
    written.forEach((name, middlewares) -> {
      RouteProfileDefinition before = stored.get(name);
      if (before == null || !before.middlewares().equals(middlewares)) {
        values.put(
            RouteProfiles.KEY_PREFIX + name, RouteProfiles.JSON.writeValueAsString(middlewares));
      }
    });
    consul.putValues(values);
  }

  /**
   * @throws NoRouteProfileException when there is none of this name
   * @throws RouteProfileInUseException when a Publication of this Workstation or a chain names it
   */
  public synchronized void remove(String name) {
    Map<String, RouteProfileDefinition> stored = profiles.stored(null);
    if (!stored.containsKey(name)) {
      throw new NoRouteProfileException(name);
    }
    List<String> services = publications.servicesByProfile().getOrDefault(name, List.of());
    List<String> chains =
        stored.values().stream()
            .filter(definition -> chainMembers(own(definition)).contains(name))
            .map(RouteProfileDefinition::name)
            .toList();
    if (!services.isEmpty() || !chains.isEmpty()) {
      throw new RouteProfileInUseException(name, services, chains);
    }
    consul.deleteValue(RouteProfiles.KEY_PREFIX + name);
  }

  /** The profile's own middleware, without the chain members its value copies. */
  private static Map<String, Object> own(RouteProfileDefinition definition) {
    return definition.middlewares().getOrDefault(definition.name(), Map.of());
  }

  /** Each middleware of a checked profile is a map, as the checks refuse any other. */
  @SuppressWarnings("unchecked")
  private static Map<String, Map<String, Object>> asMiddlewares(Map<String, Object> definition) {
    Map<String, Map<String, Object>> middlewares = new LinkedHashMap<>();
    definition.forEach(
        (name, middleware) -> middlewares.put(name, (Map<String, Object>) middleware));
    return middlewares;
  }

  private static List<?> chainMembers(Map<String, Object> middleware) {
    return middleware.get("chain") instanceof Map<?, ?> chain
            && chain.get("middlewares") instanceof List<?> members
        ? members
        : List.of();
  }
}
