package cc.lvhy.yard.dashboard;

import static cc.lvhy.yard.dashboard.StatusIT.detail;
import static cc.lvhy.yard.dashboard.StatusIT.part;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.Map;
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

  @Test
  void workstationWithoutTraefikHasNoTraefikStatusPart() {
    remote.whileDashboardRunsWith(
        () -> {
          JsonNode status = remote.get("/api/status").getBody();
          assertThat(status.get("parts").valueStream().map(part -> part.get("name").asString()))
              .containsExactly("Consul", "Docker");
          assertThat(status.get("overall").asString()).isNotEqualTo("failing");
        },
        "--YARD_TRAEFIK_ENABLED=false");
  }

  @Test
  void workstationWithoutTraefikStillTestsInstancesDirectly() throws IOException {
    try (ServerSocket instance = new ServerSocket(0)) {
      remote.whileDashboardRunsWith(
          () -> {
            String service = newServiceName("no-traefik");
            remote.register(service, Map.of("host", "127.0.0.1", "port", instance.getLocalPort()));
            remote.publish(service, "yard");
            JsonNode shared =
                get("/api/services/" + RemoteWorkstation.NAME + "/" + service).getBody();
            assertThat(shared.get("publication").get("scope").asString()).isEqualTo("yard");
            assertThat(shared.get("instances")).hasSize(1);

            JsonNode result = remote.get(servicePath(service) + "/reachability").getBody();

            assertThat(result.get("instances")).hasSize(1);
            assertThat(result.get("instances").get(0).get("reached").asBoolean()).isTrue();
            assertThat(result.get("routes")).isEmpty();
            assertThat(result.get("noRoute").asString())
                .isEqualTo("No Traefik on this Workstation, so only its Instances are tested.");

            remote.unpublish(service);
            JsonNode direct = remote.get(servicePath(service) + "/reachability").getBody();
            assertThat(direct.get("instances").get(0).get("reached").asBoolean()).isTrue();
            assertThat(direct.get("routes")).isEmpty();
            assertThat(direct.get("passed").asBoolean()).isTrue();
          },
          "--YARD_TRAEFIK_ENABLED=false");
    }
  }
}
