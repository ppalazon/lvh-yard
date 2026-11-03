package dev.palazon.lvhyard.dashboard.services;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Where the Route on this Workstation sends requests now, under the Instance Priority.
 *
 * @param kind the first kind in the order with a passing Instance. It is left out when no kind has
 *     one: the Route then sends requests to every Instance in range.
 * @param instances the IDs of the Instances the Route sends requests to. For the {@code alias}
 *     kind, they are Instances of the Alias's Remote Service.
 * @param servers the Instances that {@code instances} names
 * @param waiting the other kinds of the order that have an Instance in range
 */
public record Routing(
    @JsonInclude(JsonInclude.Include.NON_NULL) InstanceKind kind,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> instances,
    @JsonIgnore @Schema(hidden = true) List<Instance> servers,
    @JsonIgnore @Schema(hidden = true) List<InstanceKind> waiting) {

  /**
   * Out-of-range Instances are never routed, so they never make a kind win. The {@code alias} kind
   * counts only passing Instances, so an Alias is never used when no kind passes.
   *
   * @param aliased the Instances of the Remote Service of an active Alias with the same name, or
   *     none
   * @return empty when there is nothing to route to
   */
  static Optional<Routing> of(
      List<InstanceKind> priority, List<Instance> instances, List<Instance> aliased) {
    List<Instance> own = instances.stream().filter(instance -> !instance.outOfRange()).toList();
    Map<InstanceKind, List<Instance>> byKind =
        Map.of(
            InstanceKind.STATIC, ofKind(own, InstanceKind.STATIC),
            InstanceKind.BOUND, ofKind(own, InstanceKind.BOUND),
            InstanceKind.ALIAS,
                aliased.stream().filter(instance -> !instance.outOfRange()).toList());
    for (InstanceKind kind : priority) {
      List<Instance> passing =
          byKind.get(kind).stream()
              .filter(instance -> instance.health() == Health.PASSING)
              .toList();
      if (!passing.isEmpty()) {
        List<InstanceKind> waiting =
            priority.stream()
                .filter(other -> other != kind && !byKind.get(other).isEmpty())
                .toList();
        return Optional.of(
            new Routing(kind, passing.stream().map(Instance::id).toList(), passing, waiting));
      }
    }
    if (own.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(new Routing(null, own.stream().map(Instance::id).toList(), own, List.of()));
  }

  private static List<Instance> ofKind(List<Instance> instances, InstanceKind kind) {
    return instances.stream().filter(instance -> InstanceKind.of(instance) == kind).toList();
  }

  public boolean routes(Instance instance) {
    return instances.contains(instance.id());
  }
}
