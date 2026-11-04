package dev.palazon.lvhyard.dashboard.services;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import dev.palazon.lvhyard.dashboard.profiles.RouteProfileDefinition;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * {@code publication}, {@code hostname}, {@code priority}, {@code profiles} and {@code routing} are
 * left out of the JSON when null or empty. {@code containerBindings} include those that match no
 * running container.
 *
 * @param priority the Instance Priority of its Publication
 * @param profiles the Route Profiles of its Publication, in the order its Route applies them
 * @param profileDefinitions the definitions of {@code profiles} that its Home Workstation's Consul
 *     KV holds, by name
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
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
        @ArraySchema(
            arraySchema =
                @Schema(
                    description =
                        "The names of its Route Profiles, in the order its Route applies them"))
        List<String> profiles,
    @JsonIgnore @Schema(hidden = true) Map<String, RouteProfileDefinition> profileDefinitions,
    @JsonInclude(JsonInclude.Include.NON_NULL) Routing routing) {

  /**
   * @param stored the Route Profiles its Home Workstation's Consul KV holds, by name
   */
  Service(
      String workstation,
      String name,
      Publication publication,
      String hostname,
      List<Instance> instances,
      List<ContainerBinding> containerBindings,
      Map<String, RouteProfileDefinition> stored) {
    this(
        workstation,
        name,
        ServiceState.of(publication, instances),
        publication,
        hostname,
        instances,
        containerBindings,
        ServiceWarning.of(publication, instances, stored),
        publication == null ? null : publication.priority(),
        publication == null ? List.of() : publication.profiles(),
        publication == null ? Map.of() : definitions(publication.profiles(), stored),
        routing(publication, hostname, instances, List.of()));
  }

  private static Map<String, RouteProfileDefinition> definitions(
      List<String> profiles, Map<String, RouteProfileDefinition> stored) {
    return stored.entrySet().stream()
        .filter(entry -> profiles.contains(entry.getKey()))
        .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
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
        profiles,
        profileDefinitions,
        routing(publication, hostname, instances, aliased));
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
