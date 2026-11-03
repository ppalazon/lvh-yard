package dev.palazon.lvhyard.dashboard.services;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * {@code publication}, {@code hostname} and {@code core} are left out of the JSON when null or
 * false. {@code containerBindings} include those that match no running container.
 */
public record Service(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String workstation,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ServiceState state,
    @JsonInclude(JsonInclude.Include.NON_NULL) Publication publication,
    @JsonInclude(JsonInclude.Include.NON_NULL) String hostname,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Instance> instances,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ContainerBinding> containerBindings,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ServiceWarning> warnings,
    @JsonInclude(JsonInclude.Include.NON_DEFAULT) boolean core) {

  Service(
      String workstation,
      String name,
      Publication publication,
      String hostname,
      List<Instance> instances,
      List<ContainerBinding> containerBindings,
      boolean core) {
    this(
        workstation,
        name,
        ServiceState.of(publication, instances),
        publication,
        hostname,
        instances,
        containerBindings,
        ServiceWarning.of(publication, instances),
        core);
  }

  public boolean sharedWithYard() {
    return published() && publication.scope() == Scope.YARD;
  }

  /**
   * It needs a hostname here, and an Instance in range: a Route to an Out-of-range Instance would
   * reach this Workstation's own loopback or containers.
   */
  public boolean routed() {
    return hostname != null && instances.stream().anyMatch(instance -> !instance.outOfRange());
  }

  /** Whatever the Health of its Instances. */
  public boolean published() {
    return publication != null;
  }
}
