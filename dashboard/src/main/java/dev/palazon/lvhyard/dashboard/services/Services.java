package dev.palazon.lvhyard.dashboard.services;

import dev.palazon.lvhyard.dashboard.Workstation;
import org.springframework.stereotype.Component;

/** Creates and deletes the Services of this Workstation. */
@Component
public class Services {

  private final Workstation workstation;

  private final ServiceRecords records;

  private final Publications publications;

  private final Registrations registrations;

  private final ContainerBindings containerBindings;

  Services(
      Workstation workstation,
      ServiceRecords records,
      Publications publications,
      Registrations registrations,
      ContainerBindings containerBindings) {
    this.workstation = workstation;
    this.records = records;
    this.publications = publications;
    this.registrations = registrations;
    this.containerBindings = containerBindings;
  }

  /**
   * An empty, Unpublished Service. A Service that this Workstation already has stays as it is, also
   * one that only Foreign Instances give.
   *
   * @return {@code true} when it created the Service, {@code false} when the Service existed
   */
  public boolean create(String workstation, String service) {
    checkHome(workstation);
    Registrations.checkName(service);
    if (records.serviceExists(service)) {
      return false;
    }
    records.put(service);
    return true;
  }

  /**
   * The record of a Service of a Backup. Unlike {@link #create}, it accepts a Service that only
   * Foreign Instances give, as a Backup never holds that Service and so the import adds it.
   */
  public void restore(String workstation, String service) {
    checkHome(workstation);
    Registrations.checkName(service);
    records.put(service);
  }

  /**
   * Removes the Publication, the Static Instances, the Container Bindings with their Bound
   * Instances, and the record. Containers keep running. Foreign Instances stay, and so the Service
   * stays while they do.
   */
  public void delete(String workstation, String service) {
    checkHome(workstation);
    // A name Consul cannot look up fails here, as a 400, not in the Consul lookup.
    Registrations.checkName(service);
    if (!records.serviceExists(service)) {
      throw new UnknownServiceException(service);
    }
    publications.remove(service);
    containerBindings.removeAll(service);
    registrations.removeStaticInstances(service);
    records.delete(service);
  }

  /**
   * The order and the toggle stay over unpublish and publish, as they are not part of the
   * Publication.
   *
   * @throws UnknownServiceException when this Workstation has no Service of this name
   * @throws InvalidPriorityException when the order names an ID twice, or an ID that is not one of
   *     the Service's Instance Sources
   */
  public void setInstancePriority(
      String workstation, String service, InstancePriorityRequest request) {
    checkHome(workstation);
    Registrations.checkName(service);
    records.ensure(service);
    records.setPriority(service, request.order(), request.on());
  }

  private void checkHome(String workstation) {
    if (!this.workstation.is(workstation)) {
      throw new RemoteServiceException(workstation);
    }
  }
}
