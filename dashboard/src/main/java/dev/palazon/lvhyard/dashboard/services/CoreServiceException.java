package dev.palazon.lvhyard.dashboard.services;

/** Refused because it would break this Workstation. */
public class CoreServiceException extends RuntimeException {

  CoreServiceException(String message) {
    super(message);
  }

  static CoreServiceException cannotBe(String service, String change) {
    return new CoreServiceException(
        service
            + " is a Core Service, which LVH Yard keeps so this Workstation works: it cannot be "
            + change
            + ".");
  }
}
