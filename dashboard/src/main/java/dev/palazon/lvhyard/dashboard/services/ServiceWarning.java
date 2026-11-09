package dev.palazon.lvhyard.dashboard.services;

import com.fasterxml.jackson.annotation.JsonValue;
import dev.palazon.lvhyard.dashboard.profiles.RouteProfileDefinition;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @param kind for a script to tell warnings apart
 */
public record ServiceWarning(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Kind kind,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Severity severity,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String message) {

  ServiceWarning(Kind kind, String message) {
    this(kind, kind.severity(), message);
  }

  public enum Severity {

    /** The hostname does not work. */
    ERROR,

    /** The hostname works, but other Workstations or users can meet a problem. */
    RISK;

    @JsonValue
    public String value() {
      return name().toLowerCase();
    }
  }

  public enum Kind {

    /** Only its own Workstation can connect to a loopback address. */
    LOOPBACK_INSTANCE(Severity.RISK),

    UNPUBLISHED_CONTAINER_PORT(Severity.RISK),

    /**
     * A Route Profile asks who the user is, but only Traefik applies it, so a connection straight
     * to an Instance skips it.
     */
    ROUTE_PROFILE_AUTH(Severity.RISK),

    /** Its Home Workstation does not hold the Route Profile, so Traefik refuses the Route. */
    UNKNOWN_ROUTE_PROFILE(Severity.ERROR),

    /** Another tool registered it in Consul, so LVH Yard cannot change or remove it. */
    FOREIGN_INSTANCE(Severity.RISK),

    /**
     * Other Workstations skip Alias sources, so an Alias above another source can send their
     * requests to a different copy than this Workstation's.
     */
    ALIAS_ABOVE_SOURCE(Severity.RISK);

    private final Severity severity;

    Kind(Severity severity) {
      this.severity = severity;
    }

    public Severity severity() {
      return severity;
    }

    @JsonValue
    public String value() {
      return name().toLowerCase().replace('_', '-');
    }
  }

  /**
   * @param sources the Instance Sources in order
   * @param instancePriority whether the order is on
   * @param profiles the definitions of the Publication's Route Profiles, by name
   */
  static List<ServiceWarning> of(
      Publication publication,
      List<Instance> instances,
      List<InstanceSource> sources,
      boolean instancePriority,
      Map<String, RouteProfileDefinition> profiles) {
    if (publication == null) {
      return foreign(instances).toList();
    }
    return Stream.of(
            yardScope(publication, instances),
            aliasAbove(publication, sources, instancePriority),
            unknown(publication, profiles),
            auth(publication, instances, profiles),
            foreign(instances))
        .flatMap(warnings -> warnings)
        .toList();
  }

  /** An unpublished Service also gets it, because the Instance stays until someone removes it. */
  private static Stream<ServiceWarning> foreign(List<Instance> instances) {
    return instances.stream()
        .filter(Instance::foreign)
        .map(instance ->
            new ServiceWarning(
                Kind.FOREIGN_INSTANCE,
                "Another tool registered the Instance "
                    + instance.id()
                    + " at "
                    + instance.address()
                    + " in Consul, so LVH Yard cannot change or remove it. To remove it,"
                    + " deregister it from the Consul agent of its Home Workstation, for example"
                    + " with the HTTP API: PUT /v1/agent/service/deregister/"
                    + instance.id()
                    + "."));
  }

  /**
   * Only for Yard Scope: a Local Service is reached only from its Home Workstation, where both
   * work.
   */
  private static Stream<ServiceWarning> yardScope(
      Publication publication, List<Instance> instances) {
    if (publication.scope() != Scope.YARD) {
      return Stream.empty();
    }
    return Stream.concat(
        instances.stream()
            .filter(Instance::onLoopback)
            .map(instance ->
                new ServiceWarning(
                    Kind.LOOPBACK_INSTANCE,
                    "The Instance at "
                        + instance.address()
                        + " is on loopback, so other Workstations cannot reach it. Register it"
                        + " at the LAN address instead.")),
        instances.stream()
            .filter(Instance::unpublishedContainerPort)
            .map(instance ->
                new ServiceWarning(
                    Kind.UNPUBLISHED_CONTAINER_PORT,
                    "The Bound Instance at "
                        + instance.address()
                        + " is on a container port that is not published, so other Workstations"
                        + " cannot reach it. Publish port "
                        + instance.port()
                        + " with docker run -p or a ports: entry, and restart the container.")));
  }

  /**
   * Only for Yard Scope and while the order is on: then the Route here uses the Alias, while other
   * Workstations skip it and use the next source.
   */
  private static Stream<ServiceWarning> aliasAbove(
      Publication publication, List<InstanceSource> sources, boolean instancePriority) {
    if (publication.scope() != Scope.YARD || !instancePriority) {
      return Stream.empty();
    }
    int below = sources.size();
    while (below > 0 && sources.get(below - 1).kind() == SourceKind.ALIAS) {
      below--;
    }
    return sources.subList(0, below).stream()
        .filter(source -> source.kind() == SourceKind.ALIAS)
        .map(source ->
            new ServiceWarning(
                Kind.ALIAS_ABOVE_SOURCE,
                "The Alias to "
                    + source.id()
                    + " is above another Instance Source. Other Workstations skip Aliases, so"
                    + " their Route can use another copy than this Workstation's. To avoid this,"
                    + " move the Alias below the other sources."));
  }

  private static Stream<ServiceWarning> unknown(
      Publication publication, Map<String, RouteProfileDefinition> profiles) {
    return publication.profiles().stream()
        .filter(name -> !profiles.containsKey(name))
        .map(name ->
            new ServiceWarning(
                Kind.UNKNOWN_ROUTE_PROFILE,
                "The Route Profile "
                    + name
                    + " does not exist on the Home Workstation, so Traefik refuses the Route and"
                    + " the hostname does not answer. Add the profile again on the Home"
                    + " Workstation, or remove it from the Publication."));
  }

  private static Stream<ServiceWarning> auth(
      Publication publication,
      List<Instance> instances,
      Map<String, RouteProfileDefinition> profiles) {
    String addresses =
        instances.isEmpty()
            ? "the Instance's address and port"
            : instances.stream().map(Instance::address).collect(Collectors.joining(", "));
    return publication.profiles().stream()
        .filter(name -> profiles.containsKey(name) && profiles.get(name).asksWhoTheUserIs())
        .map(name ->
            new ServiceWarning(
                Kind.ROUTE_PROFILE_AUTH,
                "The Route Profile "
                    + name
                    + " asks who the user is only on the hostname. A connection straight to "
                    + addresses
                    + " skips it, so the Service must check its users itself."));
  }
}
