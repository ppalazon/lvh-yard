package dev.palazon.lvhyard.dashboard.routes;

import java.util.List;
import java.util.Map;

import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.palazon.lvhyard.dashboard.Workstation;
import dev.palazon.lvhyard.dashboard.routes.TraefikConfiguration.Http;
import dev.palazon.lvhyard.dashboard.routes.TraefikConfiguration.LoadBalancer;
import dev.palazon.lvhyard.dashboard.routes.TraefikConfiguration.Router;
import dev.palazon.lvhyard.dashboard.routes.TraefikConfiguration.Server;
import dev.palazon.lvhyard.dashboard.routes.TraefikConfiguration.Service;

/**
 * The Routes Traefik polls for. For now only the dashboard's own, at {@code yard.<base-domain>}.
 */
@RestController
class TraefikController {

    /** Where compose.yaml points Traefik's HTTP provider. */
    static final String PATH = "/traefik/configuration";

    /** The entry point compose.yaml gives Traefik for plain HTTP. */
    private static final String HTTP_ENTRY_POINT = "web";

    private final Workstation workstation;

    private final Environment environment;

    TraefikController(Workstation workstation, Environment environment) {
        this.workstation = workstation;
        this.environment = environment;
    }

    @GetMapping(path = PATH, produces = MediaType.APPLICATION_JSON_VALUE)
    TraefikConfiguration configuration() {
        String name = Workstation.RESERVED_NAME;
        // The port the server actually listens on, which is only known once it has started.
        String dashboard = "http://127.0.0.1:" + environment.getRequiredProperty("local.server.port");
        Router router = new Router("Host(`" + workstation.hostname(name) + "`)", List.of(HTTP_ENTRY_POINT), name);
        Service service = new Service(new LoadBalancer(List.of(new Server(dashboard))));
        return new TraefikConfiguration(new Http(Map.of(name, router), Map.of(name, service)));
    }
}
