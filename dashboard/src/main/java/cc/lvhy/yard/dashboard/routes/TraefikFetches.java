package cc.lvhy.yard.dashboard.routes;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * When Traefik last fetched its Routes. The status shows it, so a Traefik that reads another
 * dashboard, or none, is noticed.
 */
@Component
public class TraefikFetches {

  private final Clock clock;

  private volatile Instant last;

  TraefikFetches(Clock clock) {
    this.clock = clock;
  }

  void fetched() {
    last = clock.instant();
  }

  public Optional<Instant> last() {
    return Optional.ofNullable(last);
  }
}
