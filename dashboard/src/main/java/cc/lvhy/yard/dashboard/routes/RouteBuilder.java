package cc.lvhy.yard.dashboard.routes;

import cc.lvhy.yard.dashboard.Workstation;
import cc.lvhy.yard.dashboard.profiles.RouteProfileDefinition;
import cc.lvhy.yard.dashboard.routes.TraefikConfiguration.Http;
import cc.lvhy.yard.dashboard.routes.TraefikConfiguration.LoadBalancer;
import cc.lvhy.yard.dashboard.routes.TraefikConfiguration.Observability;
import cc.lvhy.yard.dashboard.routes.TraefikConfiguration.Router;
import cc.lvhy.yard.dashboard.routes.TraefikConfiguration.Server;
import cc.lvhy.yard.dashboard.services.Service;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Turns the Services into Traefik's configuration. It reads nothing itself. */
final class RouteBuilder {

  /** The entry point compose.yaml gives Traefik for plain HTTP. */
  private static final String HTTP_ENTRY_POINT = "web";

  private RouteBuilder() {}

  /** Unreachable Workstations are not among {@code services}, so their Services get no Route. */
  static TraefikConfiguration build(Workstation workstation, List<Service> services) {
    Map<String, Router> routers = new LinkedHashMap<>();
    Map<String, TraefikConfiguration.Service> backends = new LinkedHashMap<>();
    Map<String, Map<String, Object>> middlewares = new LinkedHashMap<>();
    services.stream()
        .filter(Service::routed)
        .forEach(service -> {
          List<Server> servers =
              service.routing().routedInstances().stream()
                  .map(instance -> new Server("http://" + instance.address()))
                  .toList();
          String name = name(workstation, service);
          routers.put(
              name,
              router(
                  service.hostname(),
                  name,
                  profiles(service, middlewares),
                  service.publication().accessLog()));
          backends.put(name, new TraefikConfiguration.Service(new LoadBalancer(servers)));
        });
    return new TraefikConfiguration(new Http(routers, backends, middlewares));
  }

  /**
   * Adds the middlewares of {@code service}'s Route Profiles to {@code middlewares}, and gives the
   * names its Route lists, in order. A profile its Home Workstation does not hold is still listed,
   * so Traefik refuses the Route rather than open it.
   */
  private static List<String> profiles(
      Service service, Map<String, Map<String, Object>> middlewares) {
    String home = service.workstation();
    service.profileDefinitions().values().stream()
        .map(RouteProfileDefinition::middlewares)
        .forEach(definitions ->
            definitions.forEach((name, definition) ->
                middlewares.put(middleware(home, name), renamed(home, definition))));
    return service.profiles().stream().map(profile -> middleware(home, profile)).toList();
  }

  /**
   * {@code <workstation>_<profile>}, so two Workstations' profiles never clash. A Workstation name
   * is a DNS label and never has an _, so the first _ always ends it. A - would not: {@code a} with
   * {@code b-c} and {@code a-b} with {@code c} would both give {@code a-b-c}.
   */
  private static String middleware(String home, String name) {
    return home + "_" + name;
  }

  /** {@code definition} with a chain's members named as they are in Traefik. */
  @SuppressWarnings("unchecked")
  private static Map<String, Object> renamed(String home, Map<String, Object> definition) {
    if (!(definition.get("chain") instanceof Map<?, ?> chain)
        || !(chain.get("middlewares") instanceof List<?> members)) {
      return definition;
    }
    Map<String, Object> renamedChain = new LinkedHashMap<>((Map<String, Object>) chain);
    renamedChain.put(
        "middlewares", members.stream().map(member -> middleware(home, (String) member)).toList());
    return Map.of("chain", renamedChain);
  }

  /**
   * A Remote Service is named {@code <service>.<workstation>}. A Service name has no dot, so it
   * never takes the name of an own Service or of the dashboard.
   */
  private static String name(Workstation workstation, Service service) {
    return name(workstation, service.workstation(), service.name());
  }

  private static String name(Workstation workstation, String home, String service) {
    return workstation.is(home) ? service : service + "." + home;
  }

  private static Router router(
      String hostname, String name, List<String> middlewares, boolean accessLog) {
    return new Router(
        "Host(`" + hostname + "`)",
        List.of(HTTP_ENTRY_POINT),
        name,
        middlewares,
        accessLog ? null : new Observability(false));
  }
}
