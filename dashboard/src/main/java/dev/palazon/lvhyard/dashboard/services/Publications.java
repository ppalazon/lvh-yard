package dev.palazon.lvhyard.dashboard.services;

import dev.palazon.lvhyard.dashboard.Workstation;
import dev.palazon.lvhyard.dashboard.consul.Consul;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * One Consul KV key per published Service, holding its Scope and Instance Priority. Publishing
 * never touches an Instance.
 */
@Component
public class Publications {

  private static final String PREFIX = "lvh-yard/publications/";

  private static final ObjectMapper JSON = JsonMapper.builder().build();

  private final Workstation workstation;

  private final Consul consul;

  private final ContainerBindings containerBindings;

  Publications(Workstation workstation, Consul consul, ContainerBindings containerBindings) {
    this.workstation = workstation;
    this.consul = consul;
    this.containerBindings = containerBindings;
  }

  /**
   * {@code workstation} must be this one. With no Scope or no priority, a new Publication gets
   * Local Scope or the default order, and an existing one keeps its own. A Core Service keeps Local
   * Scope and the default order.
   */
  public void publish(String workstation, String service, Scope scope, List<String> priority) {
    checkHome(workstation);
    Optional<CoreService> core = CoreService.named(service);
    if (core.isPresent()) {
      if (scope == Scope.YARD) {
        throw CoreServiceException.cannotBe(
            service,
            "given Yard Scope, as its Instance is on loopback, so other Workstations could not"
                + " reach it");
      }
      if (priority != null) {
        throw CoreServiceException.cannotBe(
            service, "given an Instance Priority, as it has only its own Instance");
      }
      keepCorePublication(core.get());
      return;
    }
    Registrations.checkName(service);
    List<InstanceKind> kinds = priority == null ? null : InstanceKind.priority(priority);
    checkExists(service);
    Publication existing = all().get(service);
    Publication chosen =
        new Publication(
            scope != null ? scope : existing == null ? Scope.LOCAL : existing.scope(),
            kinds != null
                ? kinds
                : existing == null ? InstanceKind.DEFAULT_PRIORITY : existing.priority());
    consul.putValue(PREFIX + service, value(chosen));
  }

  /**
   * The Scope alone, as before Instance Priority, while the order is the default. So Workstations
   * on an older version still read most Publications.
   */
  private static String value(Publication publication) {
    if (publication.priority().equals(InstanceKind.DEFAULT_PRIORITY)) {
      return publication.scope().value();
    }
    return JSON.writeValueAsString(
        new StoredPublication(
            publication.scope().value(),
            publication.priority().stream().map(InstanceKind::value).toList()));
  }

  private static Publication publication(String value) {
    if (!value.startsWith("{")) {
      return new Publication(Scope.of(value));
    }
    StoredPublication stored = JSON.readValue(value, StoredPublication.class);
    try {
      return new Publication(Scope.of(stored.scope()), InstanceKind.priority(stored.priority()));
    } catch (InvalidRegistrationException e) {
      // A fault in the stored data, not a refusal of what someone asked.
      throw new IllegalStateException(e.getMessage(), e);
    }
  }

  private record StoredPublication(String scope, List<String> priority) {}

  /**
   * A Yard Scope from before it was refused for Core Services goes back to Local. No name rule
   * applies: the Core Service's name is kept for it.
   */
  void keepCorePublication(CoreService service) {
    Publication existing = all().get(service.serviceName());
    if (existing == null || existing.scope() != Scope.LOCAL) {
      consul.putValue(PREFIX + service.serviceName(), Scope.LOCAL.value());
    }
  }

  /** {@code workstation} must be this one. A Core Service cannot be unpublished. */
  public void unpublish(String workstation, String service) {
    checkHome(workstation);
    if (CoreService.isNamed(service)) {
      throw CoreServiceException.cannotBe(service, "unpublished");
    }
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
    if (consul.health(service).isEmpty()
        && !all().containsKey(service)
        && !containerBindings.all(null).containsKey(service)) {
      throw new UnknownServiceException(service);
    }
  }

  private void checkHome(String workstation) {
    if (!this.workstation.is(workstation)) {
      throw new RemoteServiceException(workstation);
    }
  }
}
