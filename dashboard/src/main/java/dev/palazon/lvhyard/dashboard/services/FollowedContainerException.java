package dev.palazon.lvhyard.dashboard.services;

/** A second Binding of the same container would only add a second Route target. */
public class FollowedContainerException extends RuntimeException {

  private final String field;

  FollowedContainerException(String container, String service) {
    this("container " + container, service, "container");
  }

  /**
   * @param follows the target as {@link ContainerBinding#follows()} says it
   * @param field the request field that names the target
   */
  FollowedContainerException(String follows, String service, String field) {
    super(
        "The "
            + follows
            + " is already followed by a Container Binding of the Service "
            + service
            + ".");
    this.field = field;
  }

  /** {@code container} or {@code composeService}, as a form shows the refusal at it. */
  public String field() {
    return field;
  }
}
