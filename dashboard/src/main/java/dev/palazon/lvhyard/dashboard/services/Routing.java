package dev.palazon.lvhyard.dashboard.services;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Where the Route on this Workstation sends requests now, under the Instance Priority.
 *
 * @param kind the first kind in the order with a passing Instance. It is left out when no kind has
 *     one: the Route then sends requests to every Instance in range.
 * @param routedInstances the Instances the Route sends requests to. For the {@code alias} kind,
 *     they are Instances of the Alias's Remote Service.
 * @param waiting the other kinds of the order that have an Instance in range
 * @param off whether the Instance Priority is off. Then the Static and Bound Instances form one
 *     group, with no kind.
 */
public record Routing(
    @JsonInclude(JsonInclude.Include.NON_NULL) InstanceKind kind,
    @JsonIgnore @Schema(hidden = true) List<Instance> routedInstances,
    @JsonIgnore @Schema(hidden = true) List<InstanceKind> waiting,
    @JsonIgnore @Schema(hidden = true) boolean off) {

  /** The IDs of {@link #routedInstances}. */
  @JsonProperty("instances")
  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  public List<String> instances() {
    return routedInstances.stream().map(Instance::id).toList();
  }

  /**
   * Out-of-range Instances are never routed, so they never make a kind win. The {@code alias} kind
   * counts only passing Instances, so an Alias is never used when no kind passes. An empty {@code
   * priority} means off: the passing Static and Bound Instances share the Route, and the Alias is
   * never used.
   *
   * @param aliased the Instances of the Remote Service of an active Alias with the same name, or
   *     none
   * @return empty when there is nothing to route to
   */
  static Optional<Routing> of(
      List<InstanceKind> priority, List<Instance> instances, List<Instance> aliased) {
    List<Instance> own = instances.stream().filter(instance -> !instance.outOfRange()).toList();
    if (priority.isEmpty()) {
      if (own.isEmpty()) {
        return Optional.empty();
      }
      List<Instance> passing = passing(own);
      return Optional.of(new Routing(null, passing.isEmpty() ? own : passing, List.of(), true));
    }
    Map<InstanceKind, List<Instance>> byKind =
        Map.of(
            InstanceKind.STATIC, ofKind(own, InstanceKind.STATIC),
            InstanceKind.BOUND, ofKind(own, InstanceKind.BOUND),
            InstanceKind.ALIAS,
                aliased.stream().filter(instance -> !instance.outOfRange()).toList());
    for (InstanceKind kind : priority) {
      List<Instance> passing = passing(byKind.get(kind));
      if (!passing.isEmpty()) {
        List<InstanceKind> waiting =
            priority.stream()
                .filter(other -> other != kind && !byKind.get(other).isEmpty())
                .toList();
        return Optional.of(new Routing(kind, passing, waiting, false));
      }
    }
    if (own.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(new Routing(null, own, List.of(), false));
  }

  private static List<Instance> passing(List<Instance> instances) {
    return instances.stream().filter(instance -> instance.health() == Health.PASSING).toList();
  }

  /**
   * Whether the Instance Priority is off and the Route uses only passing Instances. It is false
   * while off when none passes, as the Route then uses every Instance in range.
   */
  public boolean offWithPassingInstances() {
    return off && !passing(routedInstances).isEmpty();
  }

  private static List<Instance> ofKind(List<Instance> instances, InstanceKind kind) {
    return instances.stream().filter(instance -> InstanceKind.of(instance) == kind).toList();
  }

  public boolean routes(Instance instance) {
    return routedInstances.stream().anyMatch(routed -> routed.id().equals(instance.id()));
  }
}
