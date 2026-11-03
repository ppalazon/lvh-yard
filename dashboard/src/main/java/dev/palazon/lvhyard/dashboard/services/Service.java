package dev.palazon.lvhyard.dashboard.services;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * {@code publication}, {@code hostname}, {@code priority}, {@code routing} and {@code core} are
 * left out of the JSON when null or false. {@code containerBindings} include those that match no
 * running container.
 *
 * @param priority the Instance Priority of its Publication
 * @param routing where its Route on this Workstation sends requests now
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
    @JsonInclude(JsonInclude.Include.NON_NULL) List<InstanceKind> priority,
    @JsonInclude(JsonInclude.Include.NON_NULL) Routing routing,
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
        publication == null ? null : publication.priority(),
        routing(publication, hostname, instances, List.of()),
        core);
  }

  /**
   * This Service with {@code aliased}, the Instances of the Remote Service of an active Alias with
   * its name, as the {@code alias} kind of its Instance Priority.
   */
  Service withAlias(List<Instance> aliased) {
    return new Service(
        workstation,
        name,
        state,
        publication,
        hostname,
        instances,
        containerBindings,
        warnings,
        priority,
        routing(publication, hostname, instances, aliased),
        core);
  }

  private static Routing routing(
      Publication publication, String hostname, List<Instance> instances, List<Instance> aliased) {
    if (hostname == null) {
      return null;
    }
    return Routing.of(publication.priority(), instances, aliased).orElse(null);
  }

  public boolean sharedWithYard() {
    return published() && publication.scope() == Scope.YARD;
  }

  /**
   * It needs a hostname here, and an Instance in range: a Route to an Out-of-range Instance would
   * reach this Workstation's own loopback or containers. The passing Instances of an Alias with its
   * name count too.
   */
  public boolean routed() {
    return routing != null;
  }

  /** Whatever the Health of its Instances. */
  public boolean published() {
    return publication != null;
  }

  /**
   * The kinds of its Instance Priority, in order, that have an Instance in range but are not the
   * kind its Route uses now.
   */
  public List<InstanceKind> waitingKinds() {
    return routing == null ? List.of() : routing.waiting();
  }
}
