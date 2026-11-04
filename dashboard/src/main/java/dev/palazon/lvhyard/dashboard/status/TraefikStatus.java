package dev.palazon.lvhyard.dashboard.status;

import dev.palazon.lvhyard.dashboard.profiles.RouteProfile;
import dev.palazon.lvhyard.dashboard.profiles.RouteProfiles;
import dev.palazon.lvhyard.dashboard.profiles.RouteProfilesFile;
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
 * A last fetch older than {@link #STALE}, or none, is a warning: Traefik then reads another
 * dashboard, or none, and its Routes may be old. A broken {@code route-profiles.yaml}, or a refused
 * profile in it, is a warning too: Traefik then applies other profiles than the file says.
 */
@Component
@Order(StatusOrder.TRAEFIK)
class TraefikStatus implements PartReader {

  static final String NAME = "Traefik";

  /** Three of compose.yaml's poll intervals of 5 s. */
  static final Duration STALE = Duration.ofSeconds(15);

  /** How long the ping may take, well within the status's own deadline. */
  private static final Duration PING_TIMEOUT = Duration.ofSeconds(1);

  private final TraefikSettings settings;

  private final TraefikFetches fetches;

  private final RouteProfiles profiles;

  private final HttpClient http = HttpClient.newBuilder().connectTimeout(PING_TIMEOUT).build();

  TraefikStatus(TraefikSettings settings, TraefikFetches fetches, RouteProfiles profiles) {
    this.settings = settings;
    this.fetches = fetches;
    this.profiles = profiles;
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
    Optional<String> profilesFault = profilesFault(profiles.file());
    Part.Detail profilesDetail =
        new Part.Detail(
            "route profiles",
            profilesFault.orElse(profiles.names().size() + " valid"),
            profilesFault.isPresent() ? PartState.WARNING : PartState.OK);
    return Part.of(
        NAME,
        reason(ping, pingFault, age, fresh, profilesFault),
        List.of(pingDetail, fetchDetail, profilesDetail));
  }

  /** The broken file, else the refused profiles, as the Route Profiles' detail says them. */
  private static Optional<String> profilesFault(RouteProfilesFile file) {
    if (file.error() != null) {
      return Optional.of(file.error() + " The Route Profiles read before still apply.");
    }
    List<String> refused =
        file.profiles().stream()
            .filter(profile -> !profile.valid())
            .map(RouteProfile::name)
            .toList();
    if (refused.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        "route-profiles.yaml has refused profiles: "
            + String.join(", ", refused)
            + ". Run lvh-yard profiles to see why.");
  }

  private static String reason(
      URI ping,
      Optional<String> pingFault,
      Duration age,
      boolean fresh,
      Optional<String> profilesFault) {
    if (pingFault.isPresent()) {
      return "Traefik does not answer its ping at " + ping + ". Make sure that it runs.";
    }
    if (age == null) {
      return "Traefik answers, but has not fetched its Routes since it started. Make sure that it"
          + " reads this dashboard.";
    }
    if (!fresh) {
      return "Traefik answers, but last fetched its Routes "
          + age.toSeconds()
          + " s ago, more than "
          + STALE.toSeconds()
          + " s. Make sure that it reads this dashboard.";
    }
    if (profilesFault.isPresent()) {
      return "Traefik answers, but " + profilesFault.get();
    }
    return "Traefik answers, and fetched its Routes " + age.toSeconds() + " s ago.";
  }

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
      return Optional.of("no answer: " + Failures.message(e));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return Optional.of("interrupted");
    }
  }
}
