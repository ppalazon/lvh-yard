package dev.palazon.lvhyard.dashboard.services;

import dev.palazon.lvhyard.dashboard.Workstation;
import dev.palazon.lvhyard.dashboard.consul.Consul;
import java.util.Map;
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
   * publishing again changes nothing.
   */
  public void publish(String workstation, String service, Scope scope) {
    checkHome(workstation);
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
   * Publishes the Core Service {@code service} with Local Scope, unless it has a Publication
   * already. No name rule applies: a Core Service's name is kept for it.
   */
  void keepCorePublication(String service) {
    if (!all().containsKey(service)) {
      consul.putValue(PREFIX + service, Scope.LOCAL.value());
    }
  }

  /**
   * Removes the Publication of {@code service} of {@code workstation}, which must be this one, if
   * it has one.
   */
  public void unpublish(String workstation, String service) {
    checkHome(workstation);
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
