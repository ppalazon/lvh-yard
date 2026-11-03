package dev.palazon.lvhyard.dashboard.services;

import dev.palazon.lvhyard.dashboard.Workstation;
import dev.palazon.lvhyard.dashboard.consul.Consul;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.springframework.stereotype.Component;

/**
 * This Workstation's Publications, in its Consul KV: one key per published Service, holding its
 * Scope. Publishing never touches an Instance.
 */
@Component
public class Publications {

  /** Where the Publications live in the KV store, under LVH Yard's own prefix. */
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
   * Publishes {@code service} of {@code workstation}, which must be this one, with {@code scope}.
   * With no Scope, a new Publication gets Local Scope and one that exists keeps its own, so
   * publishing again changes nothing. A Core Service keeps Local Scope.
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
   * Publishes {@code service} with Local Scope, unless it has that already. One with Yard Scope,
   * from before Yard Scope was refused, is put back to Local. No name rule applies: a Core
   * Service's name is kept for it.
   */
  void keepCorePublication(CoreService service) {
    Publication existing = all().get(service.serviceName());
    if (existing == null || existing.scope() != Scope.LOCAL) {
      consul.putValue(PREFIX + service.serviceName(), Scope.LOCAL.value());
    }
  }

  /**
   * Removes the Publication of {@code service} of {@code workstation}, which must be this one, if
   * it has one. A Core Service cannot be unpublished.
   */
  public void unpublish(String workstation, String service) {
    checkHome(workstation);
    if (CoreService.isNamed(service)) {
      throw CoreServiceException.cannotBe(service, "unpublished");
    }
    checkExists(service);
    consul.deleteValue(PREFIX + service);
  }

  /** This Workstation's Publications, by Service name. */
  Map<String, Publication> all() {
    return all(null);
  }

  /**
   * The Publications in {@code datacenter}'s KV, by Service name: those of the Workstation it
   * belongs to, or this one's when it is {@code null}.
   */
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
