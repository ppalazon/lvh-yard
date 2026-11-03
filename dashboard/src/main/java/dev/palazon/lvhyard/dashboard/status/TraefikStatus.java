package dev.palazon.lvhyard.dashboard.status;

import dev.palazon.lvhyard.dashboard.reachability.TraefikSettings;
import dev.palazon.lvhyard.dashboard.routes.TraefikFetches;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Whether this Workstation's Traefik answers its ping, on its admin entry point, and when it last
 * fetched its Routes from this dashboard. A last fetch older than {@link #STALE}, or none since the
 * dashboard started, is a warning: Traefik then reads another dashboard, or none, and its Routes
 * may be old.
 */
@Component
@Order(StatusOrder.TRAEFIK)
class TraefikStatus implements StatusPart {

  static final String NAME = "Traefik";

  /** Three of compose.yaml's poll intervals of 5 s. */
  static final Duration STALE = Duration.ofSeconds(15);

  /** How long the ping may take, well within the status's own deadline. */
  private static final Duration PING_TIMEOUT = Duration.ofSeconds(1);

  private final TraefikSettings settings;

  private final TraefikFetches fetches;

  private final HttpClient http = HttpClient.newBuilder().connectTimeout(PING_TIMEOUT).build();

  TraefikStatus(TraefikSettings settings, TraefikFetches fetches) {
    this.settings = settings;
    this.fetches = fetches;
  }

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public Part read() {
    URI ping = settings.adminUrl().resolve("/ping");
    Optional<String> pingFault = pingFault(ping);
    Optional<Instant> last = fetches.last();
    Duration age = last.map(when -> Duration.between(when, Instant.now())).orElse(null);
    boolean fresh = age != null && age.compareTo(STALE) <= 0;

    Part.Detail pingDetail =
        new Part.Detail(
            "ping",
            pingFault.orElse("OK at " + ping),
            pingFault.isPresent() ? PartState.FAILING : PartState.OK);
    Part.Detail fetchDetail =
        new Part.Detail(
            "last fetch",
            age == null ? "none since the dashboard started" : age.toSeconds() + " s ago",
            fresh ? PartState.OK : PartState.WARNING);
    return Part.of(NAME, reason(ping, pingFault, age, fresh), List.of(pingDetail, fetchDetail));
  }

  private static String reason(URI ping, Optional<String> pingFault, Duration age, boolean fresh) {
    if (pingFault.isPresent()) {
      return "Traefik does not answer its ping at " + ping + ". Is it running?";
    }
    if (age == null) {
      return "Traefik answers, but has not fetched its Routes from this dashboard since it"
          + " started. Does it read another dashboard?";
    }
    if (!fresh) {
      return "Traefik answers, but last fetched its Routes from this dashboard "
          + age.toSeconds()
          + " s ago, more than "
          + STALE.toSeconds()
          + " s. Does it read another dashboard?";
    }
    return "Traefik answers, and fetched its Routes " + age.toSeconds() + " s ago.";
  }

  /** Why {@code ping} did not answer OK; none when it did. */
  private Optional<String> pingFault(URI ping) {
    try {
      HttpResponse<String> answer =
          http.send(
              HttpRequest.newBuilder(ping).timeout(PING_TIMEOUT).build(),
              HttpResponse.BodyHandlers.ofString());
      return answer.statusCode() == 200
          ? Optional.empty()
          : Optional.of("answered " + answer.statusCode());
    } catch (IOException e) {
      return Optional.of(
          "no answer: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return Optional.of("interrupted");
    }
  }
}
