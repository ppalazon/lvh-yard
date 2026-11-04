package dev.palazon.lvhyard.dashboard.services;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.palazon.lvhyard.dashboard.Workstation;
import dev.palazon.lvhyard.dashboard.consul.Consul;
import dev.palazon.lvhyard.dashboard.profiles.RouteProfiles;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * One Consul KV key per published Service, holding its Scope, Instance Priority and Route Profiles.
 * Publishing never touches an Instance.
 */
@Component
public class Publications {

  private static final String PREFIX = "lvh-yard/publications/";

  private static final ObjectMapper JSON = JsonMapper.builder().build();

  private final Workstation workstation;

  private final Consul consul;

  private final ContainerBindings containerBindings;

  private final RouteProfiles routeProfiles;

  Publications(
      Workstation workstation,
      Consul consul,
      ContainerBindings containerBindings,
      RouteProfiles routeProfiles) {
    this.workstation = workstation;
    this.consul = consul;
    this.containerBindings = containerBindings;
    this.routeProfiles = routeProfiles;
  }

  /**
   * {@code workstation} must be this one. With no Scope, no priority or no profiles, a new
   * Publication gets Local Scope, the default order or no profiles, and an existing one keeps its
   * own.
   *
   * @param change its {@code profiles} are names of this Workstation's valid Route Profiles
   */
  public void publish(String workstation, String service, PublicationRequest change) {
    checkHome(workstation);
    Scope scope = change.scope();
    List<String> priority = change.priority();
    List<String> profiles = change.profiles();
    if (profiles != null) {
      checkProfiles(profiles);
    }
    Registrations.checkName(service);
    List<InstanceKind> kinds = priority == null ? null : InstanceKind.priority(priority);
    checkExists(service);
    Publication existing = all().get(service);
    Publication chosen =
        new Publication(
            given(scope, existing, Publication::scope, Scope.LOCAL),
            given(kinds, existing, Publication::priority, InstanceKind.DEFAULT_PRIORITY),
            given(profiles, existing, Publication::profiles, List.of()));
    consul.putValue(PREFIX + service, value(chosen));
  }

  private void checkProfiles(List<String> profiles) {
    List<String> known = routeProfiles.names();
    List<String> unknown =
        profiles.stream().filter(name -> !known.contains(name)).distinct().toList();
    if (!unknown.isEmpty()) {
      throw new UnknownRouteProfileException(unknown, known);
    }
  }

  /** {@code given} when there is one, else what {@code existing} has, else {@code fallback}. */
  private static <T> T given(
      T given, Publication existing, Function<Publication, T> kept, T fallback) {
    if (given != null) {
      return given;
    }
    return existing == null ? fallback : kept.apply(existing);
  }

  /**
   * The Scope alone, as before Instance Priority, while the order is the default and there are no
   * profiles. So Workstations on an older version still read most Publications.
   */
  private static String value(Publication publication) {
    if (publication.priority().equals(InstanceKind.DEFAULT_PRIORITY)
        && publication.profiles().isEmpty()) {
      return publication.scope().value();
    }
    return JSON.writeValueAsString(
        new StoredPublication(
            publication.scope().value(),
            publication.priority().stream().map(InstanceKind::value).toList(),
            publication.profiles().isEmpty() ? null : publication.profiles()));
  }

  private static Publication publication(String value) {
    if (!value.startsWith("{")) {
      return new Publication(Scope.of(value));
    }
    StoredPublication stored = JSON.readValue(value, StoredPublication.class);
    try {
      return new Publication(
          Scope.of(stored.scope()),
          InstanceKind.priority(stored.priority()),
          stored.profiles() == null ? List.of() : stored.profiles());
    } catch (InvalidPriorityException e) {
      // A fault in the stored data, not a refusal of what someone asked.
      throw new IllegalStateException(e.getMessage(), e);
    }
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private record StoredPublication(String scope, List<String> priority, List<String> profiles) {}

  /** {@code workstation} must be this one. */
  public void unpublish(String workstation, String service) {
    checkHome(workstation);
    checkExists(service);
    consul.deleteValue(PREFIX + service);
  }

  Map<String, Publication> all() {
    return all(null);
  }

  /** {@code null} reads this Workstation's own. */
  Map<String, Publication> all(String datacenter) {
    Map<String, Publication> publications = new TreeMap<>();
    consul
        .values(datacenter, PREFIX)
        .forEach(
            (key, value) -> publications.put(key.substring(PREFIX.length()), publication(value)));
    return publications;
  }

  private void checkExists(String service) {
    if (!exists(service)) {
      throw new UnknownServiceException(service);
    }
  }

  /** It has an Instance, a Publication or a Container Binding on this Workstation. */
  boolean exists(String service) {
    return !consul.health(service).isEmpty()
        || all().containsKey(service)
        || containerBindings.all(null).containsKey(service);
  }

  private void checkHome(String workstation) {
    if (!this.workstation.is(workstation)) {
      throw new RemoteServiceException(workstation);
    }
  }
}
