package cc.lvhy.yard.dashboard.profiles;

import cc.lvhy.yard.dashboard.consul.Consul;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Lists, adds, replaces and removes this Workstation's Route Profiles in its Consul KV. Every write
 * path uses it: the API, the page, and the CLI through the API.
 */
@Component
public class RouteProfileEditor {

  private final RouteProfiles profiles;

  private final RouteProfileChecks checks;

  private final ProfileUsage usage;

  private final Consul consul;

  RouteProfileEditor(
      RouteProfiles profiles, RouteProfileChecks checks, ProfileUsage usage, Consul consul) {
    this.profiles = profiles;
    this.checks = checks;
    this.usage = usage;
    this.consul = consul;
  }

  /** By name. */
  public List<RouteProfile> list() {
    Map<String, List<String>> services = usage.servicesByProfile();
    return profiles.stored(null).values().stream()
        .map(definition ->
            new RouteProfile(
                definition.name(),
                RouteProfileChecks.type(definition.own()),
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
    return new RouteProfileDetail(
        name,
        RouteProfileChecks.type(definition.own()),
        usage.servicesByProfile().getOrDefault(name, List.of()),
        definition.own());
  }

  /**
   * Adds or replaces each profile of {@code set}, and keeps the profiles it does not name. A chain
   * may name a profile of the set or one already stored.
   *
   * @param set each profile's middleware in Traefik's format, by name
   * @throws RefusedRouteProfilesException when one profile is refused; then nothing is written
   * @throws TooManyRouteProfileWritesException when the save needs more writes than one Consul
   *     transaction takes; then nothing is written
   */
  public synchronized void save(Map<String, ?> set) {
    Map<String, RouteProfileDefinition> stored = profiles.stored(null);
    RouteProfileChecks.Checked checked = checks.check(set, stored);
    if (!checked.problems().isEmpty()) {
      throw new RefusedRouteProfilesException(checked.problems());
    }
    Map<String, String> values = new LinkedHashMap<>();
    checked
        .middlewares()
        .forEach((name, middleware) -> {
          RouteProfileDefinition before = stored.get(name);
          if (before == null || !before.own().equals(middleware)) {
            values.put(RouteProfiles.key(name), RouteProfiles.value(middleware));
          }
        });
    // Several transactions would not be all or nothing.
    if (values.size() > Consul.TRANSACTION_SIZE) {
      throw new TooManyRouteProfileWritesException(values.size(), Consul.TRANSACTION_SIZE);
    }
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
    List<String> services = usage.servicesByProfile().getOrDefault(name, List.of());
    List<String> chains =
        stored.values().stream()
            .filter(definition -> RouteProfileChecks.chainMembers(definition.own()).contains(name))
            .map(RouteProfileDefinition::name)
            .toList();
    if (!services.isEmpty() || !chains.isEmpty()) {
      throw new RouteProfileInUseException(name, services, chains);
    }
    consul.deleteValue(RouteProfiles.key(name));
  }
}
