package dev.palazon.lvhyard.dashboard.services;

/** A second Binding of the same container would only add a second Route target. */
public class FollowedContainerException extends RuntimeException {

  FollowedContainerException(String container, String service) {
    super(
        "The container "
            + container
            + " is already followed by a Container Binding of the Service "
            + service
            + ".");
  }
}
