package dev.palazon.lvhyard.dashboard.routes;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.palazon.lvhyard.dashboard.Workstation;
import dev.palazon.lvhyard.dashboard.routes.TraefikConfiguration.Http;
import dev.palazon.lvhyard.dashboard.routes.TraefikConfiguration.LoadBalancer;
import dev.palazon.lvhyard.dashboard.routes.TraefikConfiguration.Router;
import dev.palazon.lvhyard.dashboard.routes.TraefikConfiguration.Server;
import dev.palazon.lvhyard.dashboard.routes.TraefikConfiguration.Service;

/**
 * Turns the Yard's Services into Traefik's configuration: which Services get a Route, at the hostname each Service
 * carries. It reads nothing itself.
 */
final class RouteBuilder {

    /** The entry point compose.yaml gives Traefik for plain HTTP. */
    private static final String HTTP_ENTRY_POINT = "web";

    private RouteBuilder() {
    }

    /**
     * A Route at its hostname to every Instance of each published Service of this Workstation that has any, and the
     * dashboard's own at {@code yard.<base-domain>}, which no Service can replace.
     *
     * @param dashboard the URL the dashboard listens on
     */
    static TraefikConfiguration build(Workstation workstation, String dashboard,
            List<dev.palazon.lvhyard.dashboard.services.Service> services) {
        Map<String, Router> routers = new LinkedHashMap<>();
        Map<String, Service> backends = new LinkedHashMap<>();
        services.stream()
                .filter(service -> workstation.is(service.workstation()) && service.published()
                        && !service.instances().isEmpty())
                .forEach(service -> {
                    routers.put(service.name(), router(service.hostname(), service.name()));
                    backends.put(service.name(), new Service(new LoadBalancer(service.instances().stream()
                            .map(instance -> new Server("http://" + instance.address())).toList())));
                });
        // Last, so it wins over anything above with its name.
        routers.put(Workstation.RESERVED_NAME,
                router(workstation.hostname(Workstation.RESERVED_NAME), Workstation.RESERVED_NAME));
        backends.put(Workstation.RESERVED_NAME, new Service(new LoadBalancer(List.of(new Server(dashboard)))));
        return new TraefikConfiguration(new Http(routers, backends));
    }

    /** A Route from {@code hostname} to the Traefik service called {@code name}. */
    private static Router router(String hostname, String name) {
        return new Router("Host(`" + hostname + "`)", List.of(HTTP_ENTRY_POINT), name);
    }
}
