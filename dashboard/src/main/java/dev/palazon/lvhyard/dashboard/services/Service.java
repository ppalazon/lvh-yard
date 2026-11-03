package dev.palazon.lvhyard.dashboard.services;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * A Service as the Yard sees it.
 *
 * @param workstation its Home Workstation
 * @param name its name, a single DNS label
 * @param state what its Publication and the Health of its Instances add up to
 * @param publication its Publication; {@code null}, and left out of the JSON, when it is
 *     unpublished
 * @param hostname where this Workstation routes it while it has Instances; {@code null}, and left
 *     out of the JSON, when it is unpublished
 * @param yardHostname where every other Workstation routes it, {@code
 *     <service>.<workstation>.<base-domain>}; {@code null}, and left out of the JSON, unless it has
 *     Yard Scope
 * @param instances its registered Instances, ordered by host and port
 * @param containerBindings its Container Bindings, by ID, including those that match no running
 *     container
 * @param warnings why other Workstations may not reach it, though its Scope gives them a Route
 * @param core whether it is a Core Service: the dashboard, Consul or Traefik, which LVH Yard
 *     registers and publishes itself on every Workstation; left out of the JSON when false
 */
public record Service(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String workstation,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ServiceState state,
    @JsonInclude(JsonInclude.Include.NON_NULL) Publication publication,
    @JsonInclude(JsonInclude.Include.NON_NULL) String hostname,
    @JsonInclude(JsonInclude.Include.NON_NULL) String yardHostname,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Instance> instances,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ContainerBinding> containerBindings,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ServiceWarning> warnings,
    @JsonInclude(JsonInclude.Include.NON_DEFAULT) boolean core) {

  /**
   * A Service in the state, and with the warnings, its {@code publication} and {@code instances}
   * add up to.
   */
  Service(
      String workstation,
      String name,
      Publication publication,
      String hostname,
      String yardHostname,
      List<Instance> instances,
      List<ContainerBinding> containerBindings,
      boolean core) {
    this(
        workstation,
        name,
        ServiceState.of(publication, instances),
        publication,
        hostname,
        yardHostname,
        instances,
        containerBindings,
        ServiceWarning.of(publication, instances),
        core);
  }

  /** Whether it is published with Yard Scope, so every Workstation of the Yard routes it. */
  public boolean sharedWithYard() {
    return published() && publication.scope() == Scope.YARD;
  }

  /**
   * Whether this Workstation has a Route to it: it has a hostname here, and an Instance this
   * Workstation can reach (an Out-of-range Instance is on another Workstation's loopback or Docker
   * network, so a Route to it would reach this Workstation's own instead).
   */
  public boolean routed() {
    return hostname != null && instances.stream().anyMatch(instance -> !instance.outOfRange());
  }

  /** Whether it has a Publication, whatever the Health of its Instances. */
  public boolean published() {
    return publication != null;
  }
}
