package cc.lvhy.yard.dashboard.web;

import cc.lvhy.yard.dashboard.OutOfRange;
import cc.lvhy.yard.dashboard.TraefikSettings;
import cc.lvhy.yard.dashboard.Workstation;
import cc.lvhy.yard.dashboard.services.Health;
import cc.lvhy.yard.dashboard.services.Instance;
import cc.lvhy.yard.dashboard.services.InstanceSource;
import cc.lvhy.yard.dashboard.services.Routing;
import cc.lvhy.yard.dashboard.services.Scope;
import cc.lvhy.yard.dashboard.services.Service;
import cc.lvhy.yard.dashboard.services.SourceKind;
import java.util.List;

/**
 * What the Route Diagram draws for one Service, as the Workstation in use sees it. It reads
 * settings and the Service, and sends no request.
 *
 * @param hostname {@code null} when this Workstation has none for the Service
 * @param noRoute why this Workstation gives the Service no Route, or {@code null} when it does
 * @param http {@code null} when Traefik is off
 * @param https {@code null} when HTTPS is off
 * @param httpServes whether the Route answers on {@code http}
 * @param httpsServes whether the Route answers on {@code https}
 * @param balancing how the Route shares requests, or {@code null} without a Route
 * @param instancePriority whether the Route uses only the first Instance Source that passes
 * @param yardHostname what every Workstation calls a Yard Service, or {@code null} for Local Scope
 *     and for a Service that is not published
 * @param skippedByYard how many Instances other Workstations skip, because only this one can
 *     connect to them. It is 0 on a Remote Service.
 */
record RouteDiagram(
    String viewer,
    String home,
    String service,
    boolean remote,
    String hostname,
    String noRoute,
    Listener http,
    Listener https,
    boolean httpServes,
    boolean httpsServes,
    boolean clientCertificate,
    boolean mutualTls,
    boolean accessLog,
    List<RouteProfileSummary> profiles,
    String balancing,
    boolean instancePriority,
    List<Member> instances,
    Scope scope,
    String yardHostname,
    int skippedByYard) {

  /**
   * @param reach who can connect to it
   */
  record Listener(String address, String reach) {}

  /** Where an Instance's address leads, from the address alone. */
  enum Place {
    LOOPBACK("loopback"),
    DOCKER("Docker network"),
    NETWORK("network address");

    final String label;

    Place(String label) {
      this.label = label;
    }
  }

  /**
   * @param source the label of the Instance Source that gave it
   * @param routed whether the Route sends requests to it now
   * @param outOfRange whether this Workstation skips it, as a Remote Service's Instance that only
   *     its Home Workstation can connect to
   */
  record Member(
      String source,
      String address,
      Place place,
      Health health,
      boolean routed,
      boolean outOfRange) {}

  static RouteDiagram of(
      Workstation workstation, Service service, TraefikSettings traefik, OutOfRange outOfRange) {
    boolean remote = !workstation.is(service.workstation());
    boolean httpsOn = traefik.enabled() && traefik.httpsPort() != null;
    boolean clientAuth = traefik.enabled() && traefik.clientAuth();
    boolean mtls = traefik.enabled() && traefik.mtls();
    boolean route = traefik.enabled() && service.servedWith(httpsOn, clientAuth, mtls);
    boolean clientCertificate = service.requiresClientCertificate();
    Routing routing = service.routing();
    List<Member> instances =
        service.sources().stream()
            .flatMap(source -> members(service, source, routing, route, outOfRange).stream())
            .toList();
    boolean yard = service.sharedWithYard();
    return new RouteDiagram(
        workstation.name(),
        service.workstation(),
        service.name(),
        remote,
        service.hostname(),
        route ? null : noRoute(traefik, service, httpsOn, clientAuth),
        traefik.enabled() ? listener(traefik, traefik.port()) : null,
        httpsOn ? listener(traefik, traefik.httpsPort()) : null,
        route && !clientCertificate,
        route && httpsOn,
        clientCertificate,
        service.requiresMutualTls(),
        service.published() && service.publication().accessLog(),
        RouteProfileSummary.of(service),
        route ? balancing(service, routing) : null,
        service.instancePriority(),
        instances,
        service.published() ? service.publication().scope() : null,
        yard && !remote
            ? workstation.yardHostname(service.name())
            : yard ? service.hostname() : null,
        yard && !remote ? skipped(service, outOfRange) : 0);
  }

  private static Listener listener(TraefikSettings traefik, int port) {
    String bind = traefik.bindAddress();
    boolean thisComputer =
        bind.equals("127.0.0.1") || bind.equals("::1") || bind.equals("localhost");
    return new Listener(
        bind + ":" + port, thisComputer ? "this computer only" : "other computers can connect");
  }

  /** The same reasons that RouteBuilder and Service.servedWith have, in the order they apply. */
  private static String noRoute(
      TraefikSettings traefik, Service service, boolean https, boolean clientAuth) {
    if (!traefik.enabled()) {
      return "This Workstation has no Traefik, so there is no Route.";
    }
    if (!service.published()) {
      return "The Service is not published, so it has no hostname and no Route.";
    }
    if (service.hostname() == null) {
      return "The Service has Local Scope on "
          + service.workstation()
          + ", so this Workstation has no hostname for it.";
    }
    if (!service.routed()) {
      return "No Instance is in range, so there is no Route.";
    }
    if (service.requiresClientCertificate() && !(https && clientAuth)) {
      return "A Route Profile needs a Client Certificate. Here HTTPS or Client Certificate"
          + " authentication is off, so there is no Route.";
    }
    return "The Publication needs mutual TLS. Here it is off, so there is no Route.";
  }

  private static String balancing(Service service, Routing routing) {
    if (routing.offWithPassingInstances()) {
      return "Round robin over every passing Instance of every Instance Source.";
    }
    if (routing.used() == null) {
      return "No Instance passes, so the Route uses every Instance in range.";
    }
    return "Round robin over the passing Instances of "
        + InstanceSourcesCard.label(service, routing.used())
        + ".";
  }

  private static List<Member> members(
      Service service,
      InstanceSource source,
      Routing routing,
      boolean route,
      OutOfRange outOfRange) {
    String label = InstanceSourcesCard.label(service, source);
    return source.givenInstances().stream()
        .map(instance ->
            new Member(
                label,
                instance.address(),
                place(instance, outOfRange),
                instance.health(),
                route && routing != null && routing.routes(instance),
                instance.outOfRange()))
        .toList();
  }

  private static Place place(Instance instance, OutOfRange outOfRange) {
    if (instance.onLoopback()) {
      return Place.LOOPBACK;
    }
    return outOfRange.contains(instance.host()) ? Place.DOCKER : Place.NETWORK;
  }

  /** Only what this Workstation gives. The Instances of an Alias belong to another Workstation. */
  private static int skipped(Service service, OutOfRange outOfRange) {
    return (int)
        service.sources().stream()
            .filter(source -> source.kind() != SourceKind.ALIAS)
            .flatMap(source -> source.givenInstances().stream())
            .filter(instance -> outOfRange.contains(instance.host()))
            .count();
  }
}
