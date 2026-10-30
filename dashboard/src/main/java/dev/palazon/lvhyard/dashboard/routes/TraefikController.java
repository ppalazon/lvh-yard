package dev.palazon.lvhyard.dashboard.routes;

import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.palazon.lvhyard.dashboard.Workstation;
import dev.palazon.lvhyard.dashboard.services.YardReader;

/**
 * The Routes Traefik polls for: the dashboard's own, at {@code yard.<base-domain>}, one per published Service of
 * this Workstation, and one per Yard-scoped Service of every other Workstation whose Services can be read. A
 * Workstation that stops answering, or whose Services fail to read, loses its Routes at the next poll, and gets them
 * back once it is read again; the others keep theirs.
 */
@RestController
class TraefikController {

    /** Where compose.yaml points Traefik's HTTP provider. */
    static final String PATH = "/traefik/configuration";

    private final Workstation workstation;

    private final YardReader yard;

    private final Environment environment;

    TraefikController(Workstation workstation, YardReader yard, Environment environment) {
        this.workstation = workstation;
        this.yard = yard;
        this.environment = environment;
    }

    @GetMapping(path = PATH, produces = MediaType.APPLICATION_JSON_VALUE)
    TraefikConfiguration configuration() {
        // The port the server actually listens on, which is only known once it has started.
        String dashboard = "http://127.0.0.1:" + environment.getRequiredProperty("local.server.port");
        return RouteBuilder.build(workstation, dashboard, yard.routableServices());
    }
}
