package dev.palazon.lvhyard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import tools.jackson.databind.JsonNode;

/** The status of this Workstation's LVH Yard: its API, its page and their parts. */
class StatusIT extends DashboardIT {

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

  @Test
  void infrastructureTransitionEmitsStatusChangedOverEventsStream() throws Exception {
    try (EventStreamClient client = new EventStreamClient(port)) {
      GenericContainer<?> traefik = TestcontainersConfiguration.traefik(port);
      traefik.start();
      try {
        await()
            .atMost(Duration.ofSeconds(15))
            .until(() -> client.lines.contains("event:status-changed"));

        String summary = getPage("/status/summary").getBody();
        assertThat(openingTag(summary, "id=\"dot-traefik\"")).contains("aria-label=\"Traefik: OK.");

        String statusPage = getPage("/status").getBody();
        assertThat(div(statusPage, "id=\"part-traefik\"")).contains(badge("success", "✓ OK"));
      } finally {
        traefik.stop();
      }
    }
  }
}
