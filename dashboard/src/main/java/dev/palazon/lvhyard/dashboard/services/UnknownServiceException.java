package dev.palazon.lvhyard.dashboard.services;

/** A Workstation has no Service of this name: no Instance and no Publication. */
public class UnknownServiceException extends RuntimeException {

  UnknownServiceException(String service) {
    super("This Workstation has no Service " + service + ".");
  }

  UnknownServiceException(String workstation, String service) {
    super("Workstation " + workstation + " has no Service " + service + ".");
  }
}
