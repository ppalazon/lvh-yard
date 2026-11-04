package dev.palazon.lvhyard.dashboard;

import static dev.palazon.lvhyard.dashboard.StatusTest.detail;
import static dev.palazon.lvhyard.dashboard.StatusTest.part;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import tools.jackson.databind.JsonNode;

/**
 * The Traefik part of the status, with a real Traefik: whether it answers its ping, and when it
 * last fetched its Routes from the dashboard.
 */
class TraefikStatusTest extends DashboardTest {

  /** Long enough for a status kept for 5 s to be read again, and for Traefik to start polling. */
  private static final Duration CHANGE = Duration.ofSeconds(20);

  private static GenericContainer<?> traefik;

  @AfterAll
  static void stopTraefik() {
    if (traefik != null) {
      traefik.stop();
      traefik = null;
    }
  }

  private void startTraefik() {
    if (traefik == null) {
      traefik = TestcontainersConfiguration.traefik(port);
      traefik.start();
    }
  }

  @Test
  void traefikThatAnswersItsPingAndFetchesItsRoutesIsOk() {
    startTraefik();

    JsonNode traefikPart =
        await()
            .atMost(CHANGE)
            .until(
                () -> part(get("/api/status").getBody(), "Traefik"),
                part -> part.get("state").asString().equals("ok"));

    assertThat(detail(traefikPart, "ping").get("state").asString()).isEqualTo("ok");
    assertThat(detail(traefikPart, "last fetch").get("value").asString()).endsWith(" s ago");
    assertThat(traefikPart.get("reason").asString()).contains("answers");
  }

  @Test
  void brokenRouteProfilesFileIsAWarningOnThePartAndItsDot() throws IOException {
    startTraefik();
    Files.writeString(TestcontainersConfiguration.ROUTE_PROFILES, "http: [unclosed\n");
    try {
      JsonNode traefikPart =
          await()
              .atMost(CHANGE)
              .until(
                  () -> part(get("/api/status").getBody(), "Traefik"),
                  part -> part.get("state").asString().equals("warning"));

      assertThat(traefikPart.get("reason").asString()).contains("route-profiles.yaml");
      JsonNode profiles = detail(traefikPart, "route profiles");
      assertThat(profiles.get("state").asString()).isEqualTo("warning");
      assertThat(profiles.get("value").asString()).contains("not valid YAML");
      String summary = getPage("/status/summary").getBody();
      assertThat(openingTag(summary, "id=\"dot-traefik\""))
          .contains("aria-label=\"Traefik: Warning.")
          .contains("route-profiles.yaml");
      assertThat(summary).containsPattern("id=\"dot-traefik\"[^>]*>!</span>");
    } finally {
      Files.deleteIfExists(TestcontainersConfiguration.ROUTE_PROFILES);
    }
  }

  @Test
  void refusedRouteProfileIsAWarningOnThePart() throws IOException {
    startTraefik();
    Files.writeString(
        TestcontainersConfiguration.ROUTE_PROFILES,
        """
        http:
          middlewares:
            from-file:
              basicAuth:
                usersFile: /etc/traefik/users
        """);
    try {
      JsonNode traefikPart =
          await()
              .atMost(CHANGE)
              .until(
                  () -> part(get("/api/status").getBody(), "Traefik"),
                  part -> part.get("state").asString().equals("warning"));

      assertThat(traefikPart.get("reason").asString()).contains("from-file");
      assertThat(detail(traefikPart, "route profiles").get("value").asString())
          .contains("from-file");
    } finally {
      Files.deleteIfExists(TestcontainersConfiguration.ROUTE_PROFILES);
    }
  }

  @Test
  void stoppedTraefikIsFailingAndSaysWhereItsPingWasAsked() {
    stopTraefik();

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

  @Test
  void traefikThatFetchesItsRoutesElsewhereIsAWarning() {
    int[] ports = TestcontainersConfiguration.freePorts(3);
    String nowhere = "http://127.0.0.1:" + ports[2] + "/traefik/configuration";
    GenericContainer<?> elsewhere =
        TestcontainersConfiguration.traefik(ports[0], ports[1], nowhere);
    elsewhere.start();
    try {
      remote.whileDashboardRunsWith(
          () -> {
            JsonNode traefikPart =
                await()
                    .atMost(CHANGE)
                    .until(
                        () -> part(remote.status(), "Traefik"),
                        part -> detail(part, "ping").get("state").asString().equals("ok"));
            assertThat(traefikPart.get("state").asString()).isEqualTo("warning");
            assertThat(traefikPart.get("reason").asString()).contains("has not fetched its Routes");
            assertThat(detail(traefikPart, "last fetch").get("state").asString())
                .isEqualTo("warning");
          },
          "--lvh-yard.traefik.admin-port=" + ports[1]);
    } finally {
      elsewhere.stop();
    }
  }
}
