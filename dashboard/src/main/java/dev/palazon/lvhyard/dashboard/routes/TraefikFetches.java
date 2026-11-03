package dev.palazon.lvhyard.dashboard.routes;

import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * When Traefik last fetched its Routes. The status shows it, so a Traefik that reads another
 * dashboard, or none, is noticed.
 */
@Component
public class TraefikFetches {

  private volatile Instant last;

  void fetched() {
    last = Instant.now();
  }

  public Optional<Instant> last() {
    return Optional.ofNullable(last);
  }
}
