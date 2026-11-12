package cc.lvhy.yard.dashboard.reachability;

import cc.lvhy.yard.dashboard.BindAddress;
import cc.lvhy.yard.dashboard.services.Service;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * From {@code YARD_TRAEFIK_BIND_ADDRESS}, {@code YARD_HTTP_PORT} and {@code
 * YARD_TRAEFIK_ADMIN_PORT}. The admin entry point listens on loopback only (compose.yaml).
 */
@ConfigurationProperties("lvh-yard.traefik")
public record TraefikSettings(
    boolean enabled, String bindAddress, int port, int adminPort, Integer httpsPort) {

  public URI url() {
    return URI.create("http://" + BindAddress.host(bindAddress) + ":" + port);
  }

  /** The plain HTTP address of a Publication, or null when it has no hostname or needs HTTPS. */
  public String httpUrl(Service service) {
    if (service.hostname() == null || service.requiresClientCertificate()) {
      return null;
    }
    return "http://" + service.hostname() + (port == 80 ? "" : ":" + port);
  }

  /** The HTTPS address of a Publication, or null when it has no hostname or this has no HTTPS. */
  public String httpsUrl(Service service) {
    if (service.hostname() == null || !enabled || httpsPort == null) {
      return null;
    }
    return "https://" + service.hostname() + (httpsPort == 443 ? "" : ":" + httpsPort);
  }

  /** On loopback, whatever {@link #bindAddress} says. */
  public URI adminUrl() {
    return URI.create("http://127.0.0.1:" + adminPort);
  }
}
