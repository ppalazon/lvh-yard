package dev.palazon.lvhyard.dashboard.services;

/** Only its Home Workstation can change it. */
public class RemoteServiceException extends RuntimeException {

  RemoteServiceException(String workstation) {
    super("The Services of " + workstation + " can be changed only on " + workstation + ".");
  }
}
