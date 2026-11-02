package dev.palazon.lvhyard.dashboard.services;

/** No Static Instance of the Service has this ID on this Workstation. */
public class UnknownInstanceException extends RuntimeException {

  UnknownInstanceException(String service, String id) {
    super("The Service " + service + " has no Static Instance " + id + " on this Workstation.");
  }
}
