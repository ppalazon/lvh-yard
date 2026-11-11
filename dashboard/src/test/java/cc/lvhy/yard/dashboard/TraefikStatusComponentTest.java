package cc.lvhy.yard.dashboard;

import static cc.lvhy.yard.dashboard.StatusIT.detail;
import static cc.lvhy.yard.dashboard.StatusIT.part;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** The Traefik part of the status when no Traefik answers. */
class TraefikStatusComponentTest extends DashboardComponentTest {

  /** Long enough for a status kept for 5 s to be read again, and for Traefik to start polling. */
  private static final Duration CHANGE = Duration.ofSeconds(20);

  @Test
  void stoppedTraefikIsFailingAndSaysWhereItsPingWasAsked() {
    JsonNode status =
        await()
            .atMost(CHANGE)
            .until(
                () -> get("/api/status").getBody(),
                answer -> part(answer, "Traefik").get("state").asString().equals("failing"));

    JsonNode traefikPart = part(status, "Traefik");
    assertThat(traefikPart.get("reason").asString())
        .contains("does not answer its ping")
        .contains("127.0.0.1:" + TestcontainersConfiguration.TRAEFIK_ADMIN_PORT + "/ping");
    assertThat(detail(traefikPart, "ping").get("state").asString()).isEqualTo("failing");
    assertThat(status.get("overall").asString()).isEqualTo("failing");
  }
}
