package dev.palazon.lvhyard.dashboard.routes;

import dev.palazon.lvhyard.dashboard.Workstation;
import dev.palazon.lvhyard.dashboard.services.Aliases;
import dev.palazon.lvhyard.dashboard.services.Yard;
import dev.palazon.lvhyard.dashboard.services.YardReader;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A Workstation that stops answering, or whose Services fail to read, loses only its own Routes and
 * those of the Aliases to its Services, until it is read again.
 */
@RestController
class TraefikController {

  /** Where compose.yaml points Traefik's HTTP provider. */
  static final String PATH = "/traefik/configuration";

  private final Workstation workstation;

  private final YardReader yard;

  private final Aliases aliases;

  private final TraefikFetches fetches;

  TraefikController(
      Workstation workstation, YardReader yard, Aliases aliases, TraefikFetches fetches) {
    this.workstation = workstation;
    this.yard = yard;
    this.aliases = aliases;
    this.fetches = fetches;
  }

  @GetMapping(path = PATH, produces = MediaType.APPLICATION_JSON_VALUE)
  TraefikConfiguration configuration() {
    Yard routable = yard.routableYard();
    TraefikConfiguration configuration =
        RouteBuilder.build(workstation, routable.services(), aliases.of(routable));
    fetches.fetched();
    return configuration;
  }
}
