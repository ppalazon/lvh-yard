package dev.palazon.lvhyard.dashboard.services;

/** You have not created it, and no Foreign Instance gives it. */
public class UnknownServiceException extends RuntimeException {

  UnknownServiceException(String service) {
    super("This Workstation has no Service " + service + ".");
  }

  UnknownServiceException(String workstation, String service) {
    super("Workstation " + workstation + " has no Service " + service + ".");
  }
}
