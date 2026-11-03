package dev.palazon.lvhyard.dashboard.reachability;

import dev.palazon.lvhyard.dashboard.BindAddress;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where this Workstation's Traefik answers, from {@code YARD_TRAEFIK_BIND_ADDRESS}, {@code
 * YARD_HTTP_PORT} and {@code YARD_TRAEFIK_ADMIN_PORT}.
 *
 * @param bindAddress the address its entry point for plain HTTP listens on
 * @param port that entry point's port
 * @param adminPort the port of its admin entry point, with its admin page and ping, which listens
 *     on loopback only (compose.yaml)
 */
@ConfigurationProperties("lvh-yard.traefik")
public record TraefikSettings(String bindAddress, int port, int adminPort) {

  /** Its entry point for plain HTTP, as this Workstation reaches it. */
  public URI url() {
    return URI.create("http://" + BindAddress.host(bindAddress) + ":" + port);
  }

  /** Its admin entry point, on loopback whatever {@link #bindAddress} says. */
  public URI adminUrl() {
    return URI.create("http://127.0.0.1:" + adminPort);
  }
}
