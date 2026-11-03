package dev.palazon.lvhyard.dashboard.routes;

import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * When Traefik last fetched its Routes from this dashboard, since the dashboard started. The status
 * shows it, so a Traefik that reads another dashboard, or none, is noticed.
 */
@Component
public class TraefikFetches {

  private volatile Instant last;

  /** Notes that the Routes were served now. */
  void fetched() {
    last = Instant.now();
  }

  /** When the Routes were last served; none when not since the dashboard started. */
  public Optional<Instant> last() {
    return Optional.ofNullable(last);
  }
}
