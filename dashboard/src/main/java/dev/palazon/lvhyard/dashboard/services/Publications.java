package dev.palazon.lvhyard.dashboard.services;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.palazon.lvhyard.dashboard.Workstation;
import dev.palazon.lvhyard.dashboard.consul.Consul;
import dev.palazon.lvhyard.dashboard.profiles.RouteProfiles;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * One Consul KV key per published Service, holding its Scope, Instance Priority, Route Profiles and
 * whether Traefik logs its requests. Publishing never touches an Instance.
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
   * {@code workstation} must be this one. Each part of {@code change} that is left out gets the
   * default in a new Publication, and an existing one keeps its own.
   *
   * @param change its {@code profiles} are names of this Workstation's valid Route Profiles
   */
  public void publish(String workstation, String service, PublicationRequest change) {
    write(workstation, service, change, true);
  }

  /**
   * As {@link #publish}, but the Service need not exist yet. A Backup can hold a Service that has
   * only a Publication, and the Publication is what makes it exist.
   */
  public void restore(String workstation, String service, PublicationRequest change) {
    write(workstation, service, change, false);
  }

  private void write(
      String workstation, String service, PublicationRequest change, boolean mustExist) {
    checkHome(workstation);
    Scope scope = change.scope();
    List<String> priority = change.priority();
    List<String> profiles = change.profiles();
    if (profiles != null) {
      checkProfiles(profiles);
    }
    Registrations.checkName(service);
    List<InstanceKind> kinds = priority == null ? null : InstanceKind.priority(priority);
    if (mustExist) {
      checkExists(service);
    }
    Publication existing = all().get(service);
    Publication chosen =
        new Publication(
            given(scope, existing, Publication::scope, Scope.LOCAL),
            given(kinds, existing, Publication::priority, InstanceKind.DEFAULT_PRIORITY),
            given(profiles, existing, Publication::profiles, List.of()),
            given(change.accessLog(), existing, Publication::accessLog, false));
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

  /** The Scope alone while every other part is the default, so most values stay short. */
  private static String value(Publication publication) {
    if (publication.priority().equals(InstanceKind.DEFAULT_PRIORITY)
        && publication.profiles().isEmpty()
        && !publication.accessLog()) {
      return publication.scope().value();
    }
    return JSON.writeValueAsString(
        new StoredPublication(
            publication.scope().value(),
            publication.priority().stream().map(InstanceKind::value).toList(),
            publication.profiles().isEmpty() ? null : publication.profiles(),
            publication.accessLog() ? true : null));
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
          stored.profiles() == null ? List.of() : stored.profiles(),
          Boolean.TRUE.equals(stored.accessLog()));
    } catch (InvalidPriorityException e) {
      // A fault in the stored data, not a refusal of what someone asked.
      throw new IllegalStateException(e.getMessage(), e);
    }
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private record StoredPublication(
      String scope, List<String> priority, List<String> profiles, Boolean accessLog) {}

  /** {@code workstation} must be this one. */
  public void unpublish(String workstation, String service) {
    checkHome(workstation);
    checkExists(service);
    consul.deleteValue(PREFIX + service);
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
    if (!exists(service)) {
      throw new UnknownServiceException(service);
    }
  }

  /**
   * Runs {@code add}, which gives {@code service} something on this Workstation. If the Service did
   * not exist before, it then gets a Publication with Local Scope, unless {@code publish} is false.
   * An existing Service keeps its Publication, or stays Unpublished.
   *
   * @return what {@code add} returns
   */
  public <T> T addPublishingNew(String service, Boolean publish, Supplier<T> add) {
    // Before add, which makes the Service exist. Only this Workstation's Instances, Publication
    // and Bindings count, so a Service with any of them but no Publication stays Unpublished.
    // A name Consul cannot look up fails here, as a 400, not in exists.
    Registrations.checkName(service);
    boolean isNew = !exists(service);
    T added = add.get();
    if (isNew && !Boolean.FALSE.equals(publish)) {
      publish(workstation.name(), service, PublicationRequest.KEEP_ALL);
    }
    return added;
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
