package cc.lvhy.yard.dashboard.services;

import cc.lvhy.yard.dashboard.profiles.RouteProfileDefinition;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @param kind for a script to tell warnings apart
 * @param topic a few words that name the problem, the same for each warning of a kind
 */
public record ServiceWarning(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Kind kind,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Severity severity,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String topic,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String message) {

  ServiceWarning(Kind kind, String message) {
    this(kind, kind.severity(), kind.topic(), message);
  }

  public enum Severity {

    /** The hostname does not work. */
    ERROR,

    /** The hostname works, but other Workstations or users can meet a problem. */
    RISK;

    @JsonValue
    public String value() {
      return name().toLowerCase(Locale.ROOT);
    }
  }

  public enum Kind {

    /** Only its own Workstation can connect to a loopback address. */
    LOOPBACK_INSTANCE(Severity.RISK, "Loopback Instances"),

    UNPUBLISHED_CONTAINER_PORT(Severity.RISK, "Container port"),

    /**
     * A Route Profile asks who the user is, but only Traefik applies it, so a connection straight
     * to an Instance skips it.
     */
    ROUTE_PROFILE_AUTH(Severity.RISK, "Authenticated Route"),

    /** Its Home Workstation does not hold the Route Profile, so Traefik refuses the Route. */
    UNKNOWN_ROUTE_PROFILE(Severity.ERROR, "Unknown profile"),

    /**
     * This Workstation has no HTTPS or no client authentication for a Publication that requires a
     * Client Certificate.
     */
    CLIENT_CERTIFICATE_UNAVAILABLE(Severity.ERROR, "Client Certificate"),

    /** This Workstation has no mutual TLS for a Publication that requires it. */
    MUTUAL_TLS_UNAVAILABLE(Severity.ERROR, "Mutual TLS"),

    /**
     * Traefik answers on HTTPS, but calls an Instance over plain HTTP on the network, so anyone on
     * that network can read the traffic.
     */
    UNENCRYPTED_INSTANCE(Severity.RISK, "Plain HTTP (Traefik -> Instances)"),

    /** Another tool registered it in Consul, so LVH Yard cannot change or remove it. */
    FOREIGN_INSTANCE(Severity.RISK, "Foreign Instance"),

    /**
     * Other Workstations skip Alias sources, so an Alias above another source can send their
     * requests to a different copy than this Workstation's.
     */
    ALIAS_ABOVE_SOURCE(Severity.RISK, "Alias order");

    private final Severity severity;
    private final String topic;

    Kind(Severity severity, String topic) {
      this.severity = severity;
      this.topic = topic;
    }

    /** The short name of the problem. The dashboard shows it as the title of the alert. */
    public String topic() {
      return topic;
    }

    public Severity severity() {
      return severity;
    }

    @JsonValue
    public String value() {
      return name().toLowerCase(Locale.ROOT).replace('_', '-');
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
      Map<String, RouteProfileDefinition> profiles,
      boolean noHttpsAtHome,
      boolean noClientAuthAtHome,
      boolean noMtlsAtHome,
      boolean httpsAtHome) {
    if (publication == null) {
      return foreign(instances).toList();
    }
    return Stream.of(
            yardScope(publication, instances),
            aliasAbove(publication, sources, instancePriority),
            unknown(publication, profiles),
            auth(publication, instances, profiles),
            clientCertificate(publication, profiles, noHttpsAtHome, noClientAuthAtHome),
            mutualTls(publication, noMtlsAtHome),
            unencrypted(publication, instances, httpsAtHome),
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
                "The Instance "
                    + instance.id()
                    + " at "
                    + instance.address()
                    + " is registered by another tool. LVH Yard cannot change it. To remove it,"
                    + " send PUT /v1/agent/service/deregister/"
                    + instance.id()
                    + " to the Consul agent of its Home Workstation."));
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
                    instance.address()
                        + " is on loopback. Other Workstations cannot reach it. Register it at the"
                        + " LAN address.")),
        instances.stream()
            .filter(Instance::unpublishedContainerPort)
            .map(instance ->
                new ServiceWarning(
                    Kind.UNPUBLISHED_CONTAINER_PORT,
                    "The container port of "
                        + instance.address()
                        + " is not published. Other Workstations cannot reach it. Publish port "
                        + instance.port()
                        + " with docker run -p or a ports: entry. Restart the container.")));
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
                    + " is above another Instance Source. Other Workstations skip Aliases. Move it"
                    + " below the other sources."));
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
                    + " does not exist on the Home Workstation. The hostname does not answer. Add"
                    + " it there, or remove it from the Publication."));
  }

  private static Stream<ServiceWarning> clientCertificate(
      Publication publication,
      Map<String, RouteProfileDefinition> profiles,
      boolean noHttpsAtHome,
      boolean noClientAuthAtHome) {
    if (!(noHttpsAtHome || noClientAuthAtHome)
        || !requires(publication, profiles, RouteProfileDefinition::requiresClientCertificate)) {
      return Stream.empty();
    }
    String missing = noHttpsAtHome ? "HTTPS" : "client authentication";
    return Stream.of(
        new ServiceWarning(
            Kind.CLIENT_CERTIFICATE_UNAVAILABLE,
            "No "
                + missing
                + " on this Workstation. The Route requires a Client Certificate. Turn on "
                + missing
                + ", or remove the Route Profile."));
  }

  private static Stream<ServiceWarning> mutualTls(Publication publication, boolean noMtlsAtHome) {
    if (!noMtlsAtHome || !publication.mutualTls()) {
      return Stream.empty();
    }
    return Stream.of(
        new ServiceWarning(
            Kind.MUTUAL_TLS_UNAVAILABLE,
            "No mutual TLS on this Workstation. Turn it on, or turn it off for this Service."));
  }

  /**
   * Only the Instances off loopback: a connection on loopback does not leave the computer. Mutual
   * TLS is the only way to make Traefik call an Instance over TLS.
   */
  private static Stream<ServiceWarning> unencrypted(
      Publication publication, List<Instance> instances, boolean httpsAtHome) {
    if (!httpsAtHome || publication.mutualTls()) {
      return Stream.empty();
    }
    List<String> exposed =
        instances.stream()
            .filter(instance -> !instance.onLoopback())
            .map(Instance::address)
            .toList();
    if (exposed.isEmpty()) {
      return Stream.empty();
    }
    return Stream.of(
        new ServiceWarning(
            Kind.UNENCRYPTED_INSTANCE,
            "Traefik calls "
                + String.join(", ", exposed)
                + " over plain HTTP. Others on that network can read the traffic. Serve HTTPS from"
                + " the Instance. Turn on mutual TLS for this Service."));
  }

  private static boolean requires(
      Publication publication,
      Map<String, RouteProfileDefinition> profiles,
      java.util.function.Predicate<RouteProfileDefinition> rule) {
    return publication.profiles().stream()
        .map(profiles::get)
        .filter(java.util.Objects::nonNull)
        .anyMatch(rule);
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
                    + " asks who the user is only on the hostname. A direct connection to "
                    + addresses
                    + " skips it. Make the Service check its users."));
  }
}
