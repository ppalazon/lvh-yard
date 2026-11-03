package dev.palazon.lvhyard.dashboard.routes;

import dev.palazon.lvhyard.dashboard.Workstation;
import dev.palazon.lvhyard.dashboard.routes.TraefikConfiguration.Http;
import dev.palazon.lvhyard.dashboard.routes.TraefikConfiguration.LoadBalancer;
import dev.palazon.lvhyard.dashboard.routes.TraefikConfiguration.Router;
import dev.palazon.lvhyard.dashboard.routes.TraefikConfiguration.Server;
import dev.palazon.lvhyard.dashboard.routes.TraefikConfiguration.Service;
import dev.palazon.lvhyard.dashboard.services.Alias;
import dev.palazon.lvhyard.dashboard.services.CoreService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Turns the Services and Aliases into Traefik's configuration. It reads nothing itself. */
final class RouteBuilder {

  /** The entry point compose.yaml gives Traefik for plain HTTP. */
  private static final String HTTP_ENTRY_POINT = "web";

  /**
   * What an Alias's Route is named after: {@code alias_<alias>}. A Service name or a Workstation
   * name never has an _, so it never takes the name of a Service's Route.
   */
  private static final String ALIAS_PREFIX = "alias_";

  private RouteBuilder() {}

  /**
   * The dashboard's own Route comes from the {@code yard} Core Service, or goes straight to {@code
   * dashboard} while that Service is missing, so the dashboard is always reached. Unreachable
   * Workstations are not among {@code services}, so their Services get no Route.
   *
   * @param dashboard the URL the dashboard listens on
   */
  static TraefikConfiguration build(
      Workstation workstation,
      String dashboard,
      List<dev.palazon.lvhyard.dashboard.services.Service> services,
      List<Alias> aliases) {
    Map<String, Router> routers = new LinkedHashMap<>();
    Map<String, Service> backends = new LinkedHashMap<>();
    services.stream()
        .filter(dev.palazon.lvhyard.dashboard.services.Service::routed)
        .forEach(service -> {
          List<Server> servers =
              service.instances().stream()
                  .filter(instance -> !instance.outOfRange())
                  .map(instance -> new Server("http://" + instance.address()))
                  .toList();
          String name = name(workstation, service);
          routers.put(name, router(service.hostname(), name));
          backends.put(name, new Service(new LoadBalancer(servers)));
        });
    aliases.stream()
        .filter(Alias::active)
        .forEach(alias -> {
          String target = name(workstation, alias.workstation(), alias.service());
          if (backends.containsKey(target)) {
            routers.put(ALIAS_PREFIX + alias.name(), router(alias.hostname(), target));
          }
        });
    String yard = CoreService.YARD.serviceName();
    if (!routers.containsKey(yard)) {
      // The yard Core Service is not there yet, such as while Consul is not ready.
      routers.put(yard, router(workstation.hostname(yard), yard));
      backends.put(yard, new Service(new LoadBalancer(List.of(new Server(dashboard)))));
    }
    return new TraefikConfiguration(new Http(routers, backends));
  }

  /**
   * A Remote Service is named {@code <service>.<workstation>}. A Service name has no dot, so it
   * never takes the name of an own Service or of the dashboard.
   */
  private static String name(
      Workstation workstation, dev.palazon.lvhyard.dashboard.services.Service service) {
    return name(workstation, service.workstation(), service.name());
  }

  private static String name(Workstation workstation, String home, String service) {
    return workstation.is(home) ? service : service + "." + home;
  }

  private static Router router(String hostname, String name) {
    return new Router("Host(`" + hostname + "`)", List.of(HTTP_ENTRY_POINT), name);
  }
}
