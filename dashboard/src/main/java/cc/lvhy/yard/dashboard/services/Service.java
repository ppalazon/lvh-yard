package cc.lvhy.yard.dashboard.services;

import cc.lvhy.yard.dashboard.profiles.RouteProfileDefinition;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * {@code publication}, {@code hostname}, {@code profiles} and {@code routing} are left out of the
 * JSON when null or empty. {@code containerBindings} include those that match no running container.
 *
 * @param created whether its Home Workstation holds a record of it. If not, only Foreign Instances
 *     give it, and it leaves once they do. The first change to it that needs a record writes one.
 * @param empty whether it has no Instance Sources. Its Aliases count, also where it does not list
 *     them.
 * @param sources its Instance Sources, in the order of its Instance Priority. Only its Home
 *     Workstation lists its Aliases.
 * @param instancePriority whether its Route uses only the first source in the order with a passing
 *     Instance. When false, every passing Instance of every source shares the Route.
 * @param profiles the Route Profiles of its Publication, in the order its Route applies them
 * @param profileDefinitions the definitions of {@code profiles} that its Home Workstation's Consul
 *     KV holds, by name
 * @param routing where its Route on this Workstation sends requests now
 */
public record Service(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String workstation,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ServiceState state,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description =
                "Whether its Home Workstation holds a record of it. If not, only Foreign"
                    + " Instances give it, and the first change to it writes the record")
        boolean created,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "Whether it has no Instance Sources. Its Aliases count")
        boolean empty,
    @JsonInclude(JsonInclude.Include.NON_NULL) Publication publication,
    @JsonInclude(JsonInclude.Include.NON_NULL) String hostname,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Instance> instances,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ContainerBinding> containerBindings,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ServiceWarning> warnings,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<InstanceSource> sources,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description =
                "Whether its Route uses only the first Instance Source in the order with a passing"
                    + " Instance. When false, every passing Instance of every source shares the"
                    + " Route")
        boolean instancePriority,
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
   * @param home whether this Workstation is its Home Workstation, as only that one routes to
   *     Aliases
   * @param aliased whether it has an Alias. {@code sources} lists Aliases only on the Home
   *     Workstation.
   * @param stored the Route Profiles its Home Workstation's Consul KV holds, by name
   */
  Service(
      String workstation,
      String name,
      boolean created,
      Publication publication,
      String hostname,
      List<Instance> instances,
      List<ContainerBinding> containerBindings,
      List<InstanceSource> sources,
      boolean instancePriority,
      boolean aliased,
      boolean home,
      Map<String, RouteProfileDefinition> stored,
      boolean https) {
    this(
        workstation,
        name,
        ServiceState.of(publication, instances),
        created,
        instances.isEmpty() && containerBindings.isEmpty() && !aliased,
        publication,
        hostname,
        instances,
        containerBindings,
        ServiceWarning.of(publication, instances, sources, instancePriority, stored, home && !https),
        sources,
        instancePriority,
        publication == null ? List.of() : publication.profiles(),
        publication == null ? Map.of() : definitions(publication.profiles(), stored),
        routing(hostname, sources, instancePriority, home));
  }

  private static Map<String, RouteProfileDefinition> definitions(
      List<String> profiles, Map<String, RouteProfileDefinition> stored) {
    return stored.entrySet().stream()
        .filter(entry -> profiles.contains(entry.getKey()))
        .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  /**
   * This Service of the Home Workstation with {@code aliased}: the Instances of the Remote Service
   * of each Alias, by the Alias's source ID.
   */
  Service withAliases(Map<String, List<Instance>> aliased) {
    List<InstanceSource> filled =
        sources.stream()
            .map(source ->
                source.kind() == SourceKind.ALIAS && aliased.containsKey(source.id())
                    ? new InstanceSource(source.id(), source.kind(), aliased.get(source.id()))
                    : source)
            .toList();
    return new Service(
        workstation,
        name,
        state,
        created,
        empty,
        publication,
        hostname,
        instances,
        containerBindings,
        warnings,
        filled,
        instancePriority,
        profiles,
        profileDefinitions,
        routing(hostname, filled, instancePriority, true));
  }

  private static Routing routing(
      String hostname, List<InstanceSource> sources, boolean instancePriority, boolean home) {
    if (hostname == null) {
      return null;
    }
    return Routing.of(sources, instancePriority, home).orElse(null);
  }

  public boolean sharedWithYard() {
    return published() && publication.scope() == Scope.YARD;
  }

  /**
   * It needs a hostname here, and an Instance in range: a Route to an Out-of-range Instance would
   * reach this Workstation's own loopback or containers. The Instances of its Aliases count too.
   */
  public boolean routed() {
    return routing != null;
  }

  /** Whatever the Health of its Instances. */
  public boolean published() {
    return publication != null;
  }

  public boolean requiresClientCertificate() {
    return profileDefinitions.values().stream()
        .anyMatch(RouteProfileDefinition::requiresClientCertificate);
  }

  /**
   * The sources, in order, that have an Instance in range but are not the source its Route uses
   * now.
   */
  public List<InstanceSource> waitingSources() {
    return routing == null ? List.of() : routing.waitingSources();
  }
}
