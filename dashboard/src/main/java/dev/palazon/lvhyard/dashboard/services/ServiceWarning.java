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
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String message) {

  public enum Kind {

    /** Only its own Workstation can connect to a loopback address. */
    LOOPBACK_INSTANCE,

    UNPUBLISHED_CONTAINER_PORT,

    /**
     * A Route Profile asks who the user is, but only Traefik applies it, so a connection straight
     * to an Instance skips it.
     */
    ROUTE_PROFILE_AUTH,

    /** Its Home Workstation does not hold the Route Profile, so Traefik refuses the Route. */
    UNKNOWN_ROUTE_PROFILE;

    @JsonValue
    public String value() {
      return name().toLowerCase().replace('_', '-');
    }
  }

  /**
   * @param profiles the definitions of the Publication's Route Profiles, by name
   */
  static List<ServiceWarning> of(
      Publication publication,
      List<Instance> instances,
      Map<String, RouteProfileDefinition> profiles) {
    if (publication == null) {
      return List.of();
    }
    return Stream.of(
            yardScope(publication, instances),
            unknown(publication, profiles),
            auth(publication, instances, profiles))
        .flatMap(warnings -> warnings)
        .toList();
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
                    + " the hostname does not answer. Add the profile to route-profiles.yaml"
                    + " again, or remove it from the Publication."));
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
