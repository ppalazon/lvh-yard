package dev.palazon.lvhyard.dashboard.services;

public class UnknownContainerBindingException extends RuntimeException {

  UnknownContainerBindingException(String service, String id) {
    super("The Service " + service + " has no Container Binding " + id + " on this Workstation.");
  }
}
