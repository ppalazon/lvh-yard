package dev.palazon.lvhyard.dashboard.services;

public class UnknownInstanceException extends RuntimeException {

  UnknownInstanceException(String service, String id) {
    super("The Service " + service + " has no Static Instance " + id + " on this Workstation.");
  }
}
