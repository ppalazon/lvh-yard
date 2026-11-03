package dev.palazon.lvhyard.dashboard.services;

import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.stream.Stream;

/**
 * @param kind for a script to tell warnings apart
 */
public record ServiceWarning(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Kind kind,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String message) {

  public enum Kind {

    /** Only its own Workstation can connect to a loopback address. */
    LOOPBACK_INSTANCE,

    UNPUBLISHED_CONTAINER_PORT;

    @JsonValue
    public String value() {
      return name().toLowerCase().replace('_', '-');
    }
  }

  /**
   * Only for Yard Scope: a Local Service is reached only from its Home Workstation, where both
   * work.
   */
  static List<ServiceWarning> of(Publication publication, List<Instance> instances) {
    if (publication == null || publication.scope() != Scope.YARD) {
      return List.of();
    }
    return Stream.concat(
            instances.stream()
                .filter(Instance::onLoopback)
                .map(instance ->
                    new ServiceWarning(
                        Kind.LOOPBACK_INSTANCE,
                        "The Instance at "
                            + instance.address()
                            + " is on a loopback address, so other Workstations cannot"
                            + " reach it. Register it at its Workstation's LAN address instead.")),
            instances.stream()
                .filter(Instance::unpublishedContainerPort)
                .map(instance ->
                    new ServiceWarning(
                        Kind.UNPUBLISHED_CONTAINER_PORT,
                        "The Bound Instance at "
                            + instance.address()
                            + " is on a container port that is not published on the host, so other"
                            + " Workstations cannot reach it. Publish the port, with docker run -p"
                            + " "
                            + instance.port()
                            + " or a ports: entry in the Compose file, and restart the"
                            + " container.")))
        .toList();
  }
}
