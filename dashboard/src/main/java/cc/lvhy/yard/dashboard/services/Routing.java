package cc.lvhy.yard.dashboard.services;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Optional;

/**
 * Where the Route on this Workstation sends requests now, under the Instance Priority.
 *
 * @param used the first Instance Source in the order with a passing Instance. It is {@code null}
 *     when the Instance Priority is off, or when no source has one: the Route then sends requests
 *     to every Instance in range.
 * @param routedInstances the Instances the Route sends requests to
 * @param waitingSources the other sources that have an Instance in range, in order
 * @param off whether the Instance Priority is off. It is left out of the JSON when false.
 */
public record Routing(
    @JsonIgnore @Schema(hidden = true) InstanceSource used,
    @JsonIgnore @Schema(hidden = true) List<Instance> routedInstances,
    @JsonIgnore @Schema(hidden = true) List<InstanceSource> waitingSources,
    @JsonInclude(JsonInclude.Include.NON_DEFAULT) boolean off) {

  @JsonProperty("source")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Schema(description = "The ID of the Instance Source that the Route uses now")
  public String source() {
    return used == null ? null : used.id();
  }

  @JsonProperty("kind")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Schema(description = "The kind of the Instance Source that the Route uses now")
  public SourceKind kind() {
    return used == null ? null : used.kind();
  }

  @JsonProperty("waiting")
  @ArraySchema(
      arraySchema =
          @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              description =
                  "The IDs of the other Instance Sources that have an Instance in range, in order"))
  public List<String> waiting() {
    return waitingSources.stream().map(InstanceSource::id).toList();
  }

  /** The IDs of {@link #routedInstances}. */
  @JsonProperty("instances")
  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  public List<String> instances() {
    return routedInstances.stream().map(Instance::id).toList();
  }

  /**
   * The one Route-choice rule (ADR-0009). Out-of-range Instances never count. On any Workstation
   * but the Home Workstation, Alias sources are left out, so no Route depends on a third
   * Workstation.
   *
   * @param sources in the order of the Instance Priority
   * @param priority whether the Instance Priority is on
   * @param home whether this Workstation is the Service's Home Workstation
   * @return empty when there is nothing in range to route to
   */
  static Optional<Routing> of(List<InstanceSource> sources, boolean priority, boolean home) {
    List<InstanceSource> inRange =
        sources.stream()
            .filter(source -> home || source.kind() != SourceKind.ALIAS)
            .filter(source -> !inRange(source).isEmpty())
            .toList();
    List<Instance> all = inRange.stream().flatMap(source -> inRange(source).stream()).toList();
    if (all.isEmpty()) {
      return Optional.empty();
    }
    if (!priority) {
      List<Instance> passing = passing(all);
      return Optional.of(new Routing(null, passing.isEmpty() ? all : passing, List.of(), true));
    }
    for (InstanceSource source : inRange) {
      List<Instance> passing = passing(inRange(source));
      if (!passing.isEmpty()) {
        List<InstanceSource> waiting = inRange.stream().filter(other -> other != source).toList();
        return Optional.of(new Routing(source, passing, waiting, false));
      }
    }
    return Optional.of(new Routing(null, all, List.of(), false));
  }

  private static List<Instance> inRange(InstanceSource source) {
    return source.givenInstances().stream().filter(instance -> !instance.outOfRange()).toList();
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

  public boolean routes(Instance instance) {
    return routedInstances.stream().anyMatch(routed -> routed.id().equals(instance.id()));
  }
}
