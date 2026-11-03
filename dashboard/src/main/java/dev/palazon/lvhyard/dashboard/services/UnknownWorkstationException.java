package dev.palazon.lvhyard.dashboard.services;

/** Neither this one nor any datacenter federated with it. */
public class UnknownWorkstationException extends RuntimeException {

  UnknownWorkstationException(String workstation) {
    super("The Yard has no Workstation " + workstation + ".");
  }
}
