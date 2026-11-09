package dev.palazon.lvhyard.dashboard.services;

import dev.palazon.lvhyard.dashboard.Workstation;
import dev.palazon.lvhyard.dashboard.consul.Consul;
import org.springframework.stereotype.Component;

/** Creates and deletes the Services of this Workstation. */
@Component
public class Services {

  private final Workstation workstation;

  private final Consul consul;

  private final ServiceRecords records;

  private final Publications publications;

  private final Registrations registrations;

  private final ContainerBindings containerBindings;

  Services(
      Workstation workstation,
      Consul consul,
      ServiceRecords records,
      Publications publications,
      Registrations registrations,
      ContainerBindings containerBindings) {
    this.workstation = workstation;
    this.consul = consul;
    this.records = records;
    this.publications = publications;
    this.registrations = registrations;
    this.containerBindings = containerBindings;
  }

  /**
   * An empty, Unpublished Service. A Service that only Foreign Instances give can get its record
   * too, so that you can publish it.
   *
   * @throws ServiceExistsException when this Workstation already has a record of this name
   */
  public void create(String workstation, String service) {
    checkHome(workstation);
    Registrations.checkName(service);
    if (records.exists(service)) {
      throw new ServiceExistsException(service);
    }
    records.put(service);
  }

  /**
   * Removes the Publication, the Static Instances, the Container Bindings with their Bound
   * Instances, and the record. Containers keep running. Foreign Instances stay, and so the Service
   * stays while they do.
   */
  public void delete(String workstation, String service) {
    checkHome(workstation);
    // A name Consul cannot look up fails here, as a 400, not in consul.health.
    Registrations.checkName(service);
    if (!records.exists(service) && consul.health(service).isEmpty()) {
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
   * @throws UnknownServiceException when you have not created the Service
   * @throws InvalidPriorityException when the order names an ID twice, or an ID that is not one of
   *     the Service's Instance Sources
   */
  public void setInstancePriority(
      String workstation, String service, InstancePriorityRequest request) {
    checkHome(workstation);
    Registrations.checkName(service);
    records.setPriority(service, request.order(), request.on());
  }

  private void checkHome(String workstation) {
    if (!this.workstation.is(workstation)) {
      throw new RemoteServiceException(workstation);
    }
  }
}
