package dev.palazon.lvhyard.dashboard.services;

/**
 * A change to a Core Service that is refused, because it would break this Workstation: removing,
 * editing or binding its Instances, unpublishing it, or giving it Yard Scope. The message explains
 * why to the person who asked.
 */
public class CoreServiceException extends RuntimeException {

  CoreServiceException(String message) {
    super(message);
  }

  /** Refuses {@code change}, such as "removed", to the Core Service {@code service}. */
  static CoreServiceException cannotBe(String service, String change) {
    return new CoreServiceException(
        service
            + " is a Core Service, which LVH Yard keeps so this Workstation works: it cannot be "
            + change
            + ".");
  }
}
