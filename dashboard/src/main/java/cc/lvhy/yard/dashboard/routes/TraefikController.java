package cc.lvhy.yard.dashboard.routes;

import cc.lvhy.yard.dashboard.TraefikSettings;
import cc.lvhy.yard.dashboard.Workstation;
import cc.lvhy.yard.dashboard.services.YardReader;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A Workstation that stops answering, or whose Services fail to read, loses only its own Routes and
 * the Alias sources to its Services, until it is read again.
 */
@RestController
class TraefikController {

  /** Where compose.yaml points Traefik's HTTP provider. */
  static final String PATH = "/traefik/configuration";

  private final Workstation workstation;

  private final YardReader yard;

  private final TraefikFetches fetches;
  private final TraefikSettings settings;

  TraefikController(
      Workstation workstation, YardReader yard, TraefikFetches fetches, TraefikSettings settings) {
    this.settings = settings;
    this.workstation = workstation;
    this.yard = yard;
    this.fetches = fetches;
  }

  @GetMapping(path = PATH, produces = MediaType.APPLICATION_JSON_VALUE)
  TraefikConfiguration configuration() {
    TraefikConfiguration configuration =
        RouteBuilder.build(
            workstation,
            yard.routableYard().services(),
            settings.enabled() && settings.httpsPort() != null);
    fetches.fetched();
    return configuration;
  }
}
