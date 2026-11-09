package dev.palazon.lvhyard.dashboard.services;

import dev.palazon.lvhyard.dashboard.Workstation;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The Aliases of this Workstation's Services. Aliases never change what other Workstations see, as
 * no other Workstation reads them.
 */
@Component
public class Aliases {

  private static final Logger LOG = LoggerFactory.getLogger(Aliases.class);

  private static final String NO_WORKSTATION = "The Yard has no Workstation ";

  private final Workstation workstation;

  private final ServiceRecords records;

  private final YardReader yard;

  private final Publications publications;

  Aliases(
      Workstation workstation, ServiceRecords records, YardReader yard, Publications publications) {
    this.workstation = workstation;
    this.records = records;
    this.yard = yard;
    this.publications = publications;
  }

  /**
   * Adds an Alias at the end of {@code service}'s order. Its Remote Service must have Yard Scope
   * now. Later, the Alias stays when its Remote Service loses Yard Scope or its Workstation stops
   * answering. An existing Service keeps its Publication.
   *
   * @param create when {@code service} has no record, true creates it and publishes it with Local
   *     Scope, so that its hostname works at once. It is created only after every check passes.
   * @throws InvalidRegistrationException when a name is not one DNS label, the Workstation is this
   *     one, or the Remote Service is not there with Yard Scope now
   * @throws UnknownServiceException when you have not created {@code service} and {@code create} is
   *     false
   * @throws AliasExistsException when {@code service} already has an Alias to this Remote Service
   */
  public Alias add(String service, String home, String remoteService, boolean create) {
    Registrations.checkName(service);
    checkTarget("workstation", "Workstation", home);
    checkTarget("service", "Service", remoteService);
    checkRemote(home);
    String id = Alias.id(home, remoteService);
    Optional<ServiceRecords.ServiceRecord> record = records.get(service);
    if (record.isEmpty() && !create) {
      throw new UnknownServiceException(service);
    }
    if (record.isPresent() && record.get().aliases().contains(id)) {
      throw new AliasExistsException(service, id);
    }
    Alias target = alias(home, remoteService);
    if (!target.active() && target.state() != Alias.State.UNAVAILABLE) {
      throw new InvalidRegistrationException(
          target.state() == Alias.State.UNREACHABLE || target.reason().startsWith(NO_WORKSTATION)
              ? "workstation"
              : "service",
          target.reason()
              + " Only a Remote Service with Yard Scope can be the target of an Alias.");
    }
    if (record.isEmpty()) {
      records.put(service);
      publications.publish(
          workstation.name(), service, new PublicationRequest(Scope.LOCAL, null, null));
    }
    records.addAlias(service, id);
    return target;
  }

  /**
   * Adds an Alias of a Backup. Unlike {@link #add}, it does not read the Remote Service, because
   * the Alias existed before and stays whatever its Remote Service does now.
   *
   * @throws InvalidRegistrationException when a name is not one DNS label, or the Workstation is
   *     this one
   * @throws UnknownServiceException when you have not created {@code service}
   * @throws AliasExistsException when {@code service} already has an Alias to this Remote Service
   */
  public void restore(String service, String home, String remoteService) {
    Registrations.checkName(service);
    checkTarget("workstation", "Workstation", home);
    checkTarget("service", "Service", remoteService);
    checkRemote(home);
    records.addAlias(service, Alias.id(home, remoteService));
  }

  private void checkRemote(String home) {
    if (workstation.is(home)) {
      throw new InvalidRegistrationException(
          "workstation",
          "An Alias is only for a Remote Service, and " + home + " is this Workstation.");
    }
  }

  /** A target name goes into a Consul KV value and a page link, so it must be one DNS label. */
  public static void checkTarget(String field, String kind, String name) {
    if (name == null || !Registrations.isDnsLabel(name)) {
      throw new InvalidRegistrationException(
          field,
          "The "
              + kind
              + " name "
              + name
              + " is not a single DNS label: use 1 to 63 lowercase letters, digits and -, not"
              + " starting or ending with -.");
    }
  }

  /**
   * @throws UnknownServiceException when you have not created {@code service}
   * @throws UnknownAliasException when {@code service} has no Alias to this Remote Service
   */
  public void remove(String service, String home, String remoteService) {
    Registrations.checkName(service);
    records.removeAlias(service, Alias.id(home, remoteService));
  }

  /**
   * The Aliases of this Workstation's {@code service}, in the order of its Instance Priority.
   *
   * @throws UnknownServiceException when you have not created {@code service}
   */
  public List<Alias> of(String service) {
    Registrations.checkName(service);
    ServiceRecords.ServiceRecord record =
        records.get(service).orElseThrow(() -> new UnknownServiceException(service));
    return InstanceSource.order(record.order(), record.aliases()).stream()
        .map(id -> {
          String[] target = id.split("/", 2);
          return alias(target[0], target.length < 2 ? "" : target[1]);
        })
        .toList();
  }

  /**
   * Any fault reading the other Workstation makes the Alias unreachable, so it never breaks this
   * Workstation's own pages.
   */
  private Alias alias(String home, String service) {
    String id = Alias.id(home, service);
    Optional<Service> target;
    try {
      target = yard.service(home, service);
    } catch (UnknownWorkstationException e) {
      return new Alias(id, home, service, Alias.State.MISSING, NO_WORKSTATION + home + ".");
    } catch (RuntimeException fault) {
      LOG.debug("The Alias to {} cannot read its Remote Service", id, fault);
      return new Alias(
          id,
          home,
          service,
          Alias.State.UNREACHABLE,
          "Workstation " + home + " is unreachable: its Consul does not answer.");
    }
    if (target.isEmpty()) {
      return new Alias(
          id,
          home,
          service,
          Alias.State.MISSING,
          "Workstation " + home + " has no Service " + service + ".");
    }
    if (!target.get().published()) {
      return new Alias(
          id, home, service, Alias.State.NOT_SHARED, home + " has not published " + service + ".");
    }
    if (!target.get().sharedWithYard()) {
      return new Alias(
          id,
          home,
          service,
          Alias.State.NOT_SHARED,
          home + " publishes " + service + " with Local Scope only.");
    }
    if (target.get().instances().stream().allMatch(Instance::outOfRange)) {
      return new Alias(
          id,
          home,
          service,
          Alias.State.UNAVAILABLE,
          service + " has no Instance this Workstation can reach.");
    }
    return new Alias(id, home, service, Alias.State.ACTIVE, null);
  }
}
