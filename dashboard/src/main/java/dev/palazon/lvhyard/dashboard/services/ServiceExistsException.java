package dev.palazon.lvhyard.dashboard.services;

/** You already created a Service of this name on this Workstation. */
public class ServiceExistsException extends RuntimeException {

  ServiceExistsException(String service) {
    super("This Workstation already has a Service " + service + ".");
  }
}
