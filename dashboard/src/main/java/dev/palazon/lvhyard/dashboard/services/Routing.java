package dev.palazon.lvhyard.dashboard.services;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Where the Route on this Workstation sends requests now, under the Instance Priority.
 *
 * @param kind the first kind in the order with a passing Instance. It is left out when no kind has
 *     one: the Route then sends requests to every Instance in range.
 * @param instances the IDs of the Instances the Route sends requests to
 */
public record Routing(
    @JsonInclude(JsonInclude.Include.NON_NULL) InstanceKind kind,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> instances) {

  /** Out-of-range Instances are never routed, so they never make a kind win. */
  static Routing of(List<InstanceKind> priority, List<Instance> instances) {
    List<Instance> inRange = instances.stream().filter(instance -> !instance.outOfRange()).toList();
    for (InstanceKind kind : priority) {
      List<String> passing =
          inRange.stream()
              .filter(instance -> InstanceKind.of(instance) == kind)
              .filter(instance -> instance.health() == Health.PASSING)
              .map(Instance::id)
              .toList();
      if (!passing.isEmpty()) {
        return new Routing(kind, passing);
      }
    }
    return new Routing(null, inRange.stream().map(Instance::id).toList());
  }

  public boolean routes(Instance instance) {
    return instances.contains(instance.id());
  }
}
