package dev.palazon.lvhyard.dashboard.services;

/** It has no record, and no Instance gives it. */
public class UnknownServiceException extends RuntimeException {

  private final String service;

  UnknownServiceException(String service) {
    super("This Workstation has no Service " + service + ".");
    this.service = service;
  }

  UnknownServiceException(String workstation, String service) {
    super("Workstation " + workstation + " has no Service " + service + ".");
    this.service = service;
  }

  /** The name, so a client can offer to create it. */
  public String service() {
    return service;
  }
}
