package dev.palazon.lvhyard.dashboard.reachability;

import dev.palazon.lvhyard.dashboard.BindAddress;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where this Workstation's Traefik answers, from {@code YARD_TRAEFIK_BIND_ADDRESS} and {@code
 * YARD_HTTP_PORT}.
 *
 * @param bindAddress the address its entry point for plain HTTP listens on
 * @param port that entry point's port
 */
@ConfigurationProperties("lvh-yard.traefik")
public record TraefikSettings(String bindAddress, int port) {

  /** Its entry point for plain HTTP, as this Workstation reaches it. */
  public URI url() {
    return URI.create("http://" + BindAddress.host(bindAddress) + ":" + port);
  }
}
