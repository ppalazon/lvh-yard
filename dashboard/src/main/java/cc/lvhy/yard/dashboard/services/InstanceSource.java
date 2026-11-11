package cc.lvhy.yard.dashboard.services;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * One thing that gives a Service Instances.
 *
 * @param id stable inside its Service: the Consul ID of a Static or Foreign Instance, the Binding
 *     ID of a Container Binding, or {@code <workstation>/<service>} of an Alias's Remote Service
 * @param givenInstances the Instances it gives now. For an Alias, they are Instances of its Remote
 *     Service.
 */
public record InstanceSource(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String id,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) SourceKind kind,
    @JsonIgnore @Schema(hidden = true) List<Instance> givenInstances) {

  public InstanceSource {
    givenInstances = List.copyOf(givenInstances);
  }

  /** The IDs of {@link #givenInstances}. */
  @JsonProperty("instances")
  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  public List<String> instances() {
    return givenInstances.stream().map(Instance::id).toList();
  }

  /** {@code sources} in the stored {@code order}, as {@link #order} puts their IDs. */
  static List<InstanceSource> ordered(List<String> order, List<InstanceSource> sources) {
    Map<String, InstanceSource> byId =
        sources.stream()
            .collect(Collectors.toMap(InstanceSource::id, Function.identity(), (a, b) -> a));
    return order(order, byId.keySet()).stream().map(byId::get).toList();
  }

  /**
   * The IDs of {@code current} in the stored {@code order}, then the IDs that the order does not
   * name. Those go to the end, so a new source never takes over the Route. They are sorted, so
   * every reader puts them in the same place. An ID in the order whose source is gone is skipped.
   */
  static List<String> order(List<String> order, Collection<String> current) {
    Set<String> ordered = new LinkedHashSet<>();
    order.stream().filter(current::contains).forEach(ordered::add);
    List<String> unnamed = new ArrayList<>(current);
    unnamed.removeAll(ordered);
    unnamed.sort(null);
    ordered.addAll(unnamed);
    return List.copyOf(ordered);
  }
}
