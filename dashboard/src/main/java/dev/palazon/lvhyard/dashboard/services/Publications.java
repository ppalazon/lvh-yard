package dev.palazon.lvhyard.dashboard.services;

import dev.palazon.lvhyard.dashboard.Workstation;
import dev.palazon.lvhyard.dashboard.consul.Consul;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.springframework.stereotype.Component;

/**
 * One Consul KV key per published Service, holding its Scope. Publishing never touches an Instance.
 */
@Component
public class Publications {

  private static final String PREFIX = "lvh-yard/publications/";

  private final Workstation workstation;

  private final Consul consul;

  private final ContainerBindings containerBindings;

  Publications(Workstation workstation, Consul consul, ContainerBindings containerBindings) {
    this.workstation = workstation;
    this.consul = consul;
    this.containerBindings = containerBindings;
  }

  /**
   * {@code workstation} must be this one. With no Scope, a new Publication gets Local Scope and an
   * existing one keeps its own. A Core Service keeps Local Scope.
   */
  public void publish(String workstation, String service, Scope scope) {
    checkHome(workstation);
    Optional<CoreService> core = CoreService.named(service);
    if (core.isPresent()) {
      if (scope == Scope.YARD) {
        throw CoreServiceException.cannotBe(
            service,
            "given Yard Scope, as its Instance is on loopback, so other Workstations could not"
                + " reach it");
      }
      keepCorePublication(core.get());
      return;
    }
    Registrations.checkName(service);
    checkExists(service);
    Scope chosen = scope;
    if (chosen == null) {
      Publication existing = all().get(service);
      chosen = existing == null ? Scope.LOCAL : existing.scope();
    }
    consul.putValue(PREFIX + service, chosen.value());
  }

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
        .forEach((key, scope) ->
            publications.put(key.substring(PREFIX.length()), new Publication(Scope.of(scope))));
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
