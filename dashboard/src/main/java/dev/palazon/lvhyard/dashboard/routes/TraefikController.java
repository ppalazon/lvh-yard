package dev.palazon.lvhyard.dashboard.routes;

import dev.palazon.lvhyard.dashboard.BindAddress;
import dev.palazon.lvhyard.dashboard.Workstation;
import dev.palazon.lvhyard.dashboard.services.Aliases;
import dev.palazon.lvhyard.dashboard.services.Yard;
import dev.palazon.lvhyard.dashboard.services.YardReader;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Routes Traefik polls for: the dashboard's own, at {@code yard.<base-domain>}, one per
 * published Service of this Workstation, one per Yard-scoped Service of every other Workstation
 * whose Services can be read, and one per active Alias of this Workstation. A Workstation that
 * stops answering, or whose Services fail to read, loses its Routes, and the Routes of the Aliases
 * to its Services, at the next poll, and gets them back once it is read again; the others keep
 * theirs.
 */
@RestController
class TraefikController {

  /** Where compose.yaml points Traefik's HTTP provider. */
  static final String PATH = "/traefik/configuration";

  private final Workstation workstation;

  private final YardReader yard;

  private final Aliases aliases;

  private final Environment environment;

  TraefikController(
      Workstation workstation, YardReader yard, Aliases aliases, Environment environment) {
    this.workstation = workstation;
    this.yard = yard;
    this.aliases = aliases;
    this.environment = environment;
  }

  @GetMapping(path = PATH, produces = MediaType.APPLICATION_JSON_VALUE)
  TraefikConfiguration configuration() {
    // The port the server actually listens on, which is only known once it has started.
    String dashboard =
        "http://"
            + BindAddress.host(environment.getRequiredProperty("server.address"))
            + ":"
            + environment.getRequiredProperty("local.server.port");
    Yard routable = yard.routableYard();
    return RouteBuilder.build(workstation, dashboard, routable.services(), aliases.of(routable));
  }
}
