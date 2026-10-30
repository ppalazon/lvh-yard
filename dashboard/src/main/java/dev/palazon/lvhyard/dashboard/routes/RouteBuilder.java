package dev.palazon.lvhyard.dashboard.routes;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.palazon.lvhyard.dashboard.Workstation;
import dev.palazon.lvhyard.dashboard.services.Alias;
import dev.palazon.lvhyard.dashboard.routes.TraefikConfiguration.Http;
import dev.palazon.lvhyard.dashboard.routes.TraefikConfiguration.LoadBalancer;
import dev.palazon.lvhyard.dashboard.routes.TraefikConfiguration.Router;
import dev.palazon.lvhyard.dashboard.routes.TraefikConfiguration.Server;
import dev.palazon.lvhyard.dashboard.routes.TraefikConfiguration.Service;

/**
 * Turns the Yard's Services and this Workstation's Aliases into Traefik's configuration: which Services and Aliases
 * get a Route, at the hostname each one carries. It reads nothing itself.
 */
final class RouteBuilder {

    /** The entry point compose.yaml gives Traefik for plain HTTP. */
    private static final String HTTP_ENTRY_POINT = "web";

    /**
     * What an Alias's Route is named after: {@code alias_<alias>}. A Service name or a Workstation name never has an
     * _, so it never takes the name of a Service's Route.
     */
    private static final String ALIAS_PREFIX = "alias_";

    private RouteBuilder() {
    }

    /**
     * A Route at its hostname to every Instance of each Service that has a hostname and any Instance this Workstation
     * can reach (an Out-of-range Instance is on another Workstation's loopback or Docker network, so a Route to it
     * would reach this Workstation's own instead), and the
     * dashboard's own at {@code yard.<base-domain>}, which no Service can replace. A Service has a hostname here
     * when it is published on this Workstation, or published with Yard Scope on another one; the Services of an
     * unreachable Workstation are not among {@code services}, so they get no Route. Each active Alias gets a Route at
     * its hostname to the backend of the Remote Service it names, when that Service has one.
     *
     * @param dashboard the URL the dashboard listens on
     */
    static TraefikConfiguration build(Workstation workstation, String dashboard,
            List<dev.palazon.lvhyard.dashboard.services.Service> services, List<Alias> aliases) {
        Map<String, Router> routers = new LinkedHashMap<>();
        Map<String, Service> backends = new LinkedHashMap<>();
        services.stream()
                .filter(service -> service.hostname() != null)
                .forEach(service -> {
                    List<Server> servers = service.instances().stream().filter(instance -> !instance.outOfRange())
                            .map(instance -> new Server("http://" + instance.address())).toList();
                    if (servers.isEmpty()) {
                        return;
                    }
                    String name = name(workstation, service);
                    routers.put(name, router(service.hostname(), name));
                    backends.put(name, new Service(new LoadBalancer(servers)));
                });
        aliases.stream().filter(Alias::active).forEach(alias -> {
            String target = name(workstation, alias.workstation(), alias.service());
            if (backends.containsKey(target)) {
                routers.put(ALIAS_PREFIX + alias.name(), router(alias.hostname(), target));
            }
        });
        // Last, so it wins over anything above with its name.
        routers.put(Workstation.RESERVED_NAME,
                router(workstation.hostname(Workstation.RESERVED_NAME), Workstation.RESERVED_NAME));
        backends.put(Workstation.RESERVED_NAME, new Service(new LoadBalancer(List.of(new Server(dashboard)))));
        return new TraefikConfiguration(new Http(routers, backends));
    }

    /**
     * The Traefik name of {@code service}'s Route and backend: its own name on its Home Workstation, and
     * {@code <service>.<workstation>} for a Remote Service. A Service name is one DNS label, with no dot, so a Remote
     * Service never takes the name of an own one, or of the dashboard's.
     */
    private static String name(Workstation workstation, dev.palazon.lvhyard.dashboard.services.Service service) {
        return name(workstation, service.workstation(), service.name());
    }

    private static String name(Workstation workstation, String home, String service) {
        return workstation.is(home) ? service : service + "." + home;
    }

    /** A Route from {@code hostname} to the Traefik service called {@code name}. */
    private static Router router(String hostname, String name) {
        return new Router("Host(`" + hostname + "`)", List.of(HTTP_ENTRY_POINT), name);
    }
}
