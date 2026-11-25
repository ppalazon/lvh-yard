package cc.lvhy.yard.dashboard;

import static org.awaitility.Awaitility.await;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import tools.jackson.databind.JsonNode;

/** The status of this Workstation's LVH Yard: its API, its page and their parts. */
class StatusIT extends DashboardIT {

  /** Traefik starts, its ping answers, the kept status is read again, and the watcher polls. */
  private static final Duration STATE_CHANGES = Duration.ofSeconds(30);

  /** The part called {@code name} of a status. */
  static JsonNode part(JsonNode status, String name) {
    return status
        .get("parts")
        .valueStream()
        .filter(part -> part.get("name").asString().equals(name))
        .findFirst()
        .orElseThrow(() -> new AssertionError("No part " + name + " in " + status));
  }

  /** The detail called {@code name} of a part. */
  static JsonNode detail(JsonNode part, String name) {
    return part.get("details")
        .valueStream()
        .filter(detail -> detail.get("name").asString().equals(name))
        .findFirst()
        .orElseThrow(() -> new AssertionError("No detail " + name + " in " + part));
  }

  /**
   * A real Traefik that starts shows as OK on the summary and on the page, and a change of status
   * reaches an events stream. When a part signals a change is a unit test of its own, {@code
   * StatusWatcherTest}. This test never takes an event as a sign that Traefik is OK: a change of
   * any part sends one, and the status is kept for 5 s, so the page can still show the state from
   * before Traefik answered.
   */
  @Test
  void infrastructureTransitionEmitsStatusChangedOverEventsStream() throws Exception {
    try (EventStreamClient client = new EventStreamClient(port)) {
      GenericContainer<?> traefik = TestcontainersConfiguration.traefik(port);
      traefik.start();
      try {
        // The container is up when Traefik starts its provider, which is before its ping answers.
        await()
            .atMost(STATE_CHANGES)
            .until(() ->
                openingTag(getPage("/status/summary").getBody(), "id=\"dot-traefik\"")
                    .contains("aria-label=\"Traefik: OK."));
        await()
            .atMost(STATE_CHANGES)
            .until(() ->
                div(getPage("/status").getBody(), "id=\"part-traefik\"")
                    .contains(badge("success", "✓ OK")));

        // The watcher reads the status every 2 s from a status that is kept for 5 s.
        await().atMost(STATE_CHANGES).until(() -> client.lines.contains("event:status-changed"));
      } finally {
        traefik.stop();
      }
    }
  }
}
