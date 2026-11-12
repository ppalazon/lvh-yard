package cc.lvhy.yard.dashboard.services;

import cc.lvhy.yard.dashboard.Workstation;
import cc.lvhy.yard.dashboard.consul.Consul;
import cc.lvhy.yard.dashboard.profiles.RouteProfiles;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * One Consul KV key per published Service, holding its Scope, Route Profiles and whether Traefik
 * logs its requests. Publishing never touches an Instance itself: it says so, and the Bound
 * Instances follow the new Scope.
 */
@Component
public class Publications {

  private static final String PREFIX = "lvh-yard/publications/";

  private static final ObjectMapper JSON = JsonMapper.builder().build();

  private final Workstation workstation;

  private final Consul consul;

  private final ServiceRecords records;

  private final RouteProfiles routeProfiles;

  private final ApplicationEventPublisher changes;

  Publications(
      Workstation workstation,
      Consul consul,
      ServiceRecords records,
      RouteProfiles routeProfiles,
      ApplicationEventPublisher changes) {
    this.changes = changes;
    this.workstation = workstation;
    this.consul = consul;
    this.records = records;
    this.routeProfiles = routeProfiles;
  }

  /**
   * {@code workstation} must be this one. Each part of {@code change} that is left out gets the
   * default in a new Publication, and an existing one keeps its own.
   *
   * @param change its {@code profiles} are names of this Workstation's valid Route Profiles
   */
  public void publish(String workstation, String service, PublicationRequest change) {
    checkHome(workstation);
    Scope scope = change.scope();
    List<String> profiles = change.profiles();
    if (profiles != null) {
      checkProfiles(profiles);
    }
    Registrations.checkName(service);
    // The record keeps a published Service after its Foreign Instances go.
    records.ensure(service);
    Publication existing = all().get(service);
    Publication chosen =
        new Publication(
            given(scope, existing, Publication::scope, Scope.LOCAL),
            given(profiles, existing, Publication::profiles, List.of()),
            given(change.accessLog(), existing, Publication::accessLog, false));
    consul.putValue(PREFIX + service, value(chosen));
    changes.publishEvent(new PublicationsChanged());
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

  /** The Scope alone while every other part is the default, so most values stay short. */
  private static String value(Publication publication) {
    if (publication.profiles().isEmpty() && !publication.accessLog()) {
      return publication.scope().value();
    }
    return JSON.writeValueAsString(
        new StoredPublication(
            publication.scope().value(),
            publication.profiles().isEmpty() ? null : publication.profiles(),
            publication.accessLog() ? true : null));
  }

  private static Publication publication(String value) {
    if (!value.startsWith("{")) {
      return new Publication(Scope.of(value));
    }
    StoredPublication stored = JSON.readValue(value, StoredPublication.class);
    return new Publication(
        Scope.of(stored.scope()),
        stored.profiles() == null ? List.of() : stored.profiles(),
        Boolean.TRUE.equals(stored.accessLog()));
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private record StoredPublication(String scope, List<String> profiles, Boolean accessLog) {}

  /** {@code workstation} must be this one. */
  public void unpublish(String workstation, String service) {
    checkHome(workstation);
    checkExists(service);
    consul.deleteValue(PREFIX + service);
    changes.publishEvent(new PublicationsChanged());
  }

  Map<String, Publication> all() {
    return all(null);
  }

  /** The Services of this Workstation whose Publications name each Route Profile, by name. */
  public Map<String, List<String>> servicesByProfile() {
    Map<String, List<String>> services = new TreeMap<>();
    all()
        .forEach((service, publication) ->
            publication
                .profiles()
                .forEach(profile ->
                    services.computeIfAbsent(profile, name -> new ArrayList<>()).add(service)));
    return services;
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
    if (!records.serviceExists(service)) {
      throw new UnknownServiceException(service);
    }
  }

  /** Removes the Publication, if there is one. */
  void remove(String service) {
    consul.deleteValue(PREFIX + service);
  }

  private void checkHome(String workstation) {
    if (!this.workstation.is(workstation)) {
      throw new RemoteServiceException(workstation);
    }
  }
}
