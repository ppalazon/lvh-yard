package dev.palazon.lvhyard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/**
 * The first start of a dashboard sets up yard, consul-ui and traefik-admin, and then they are
 * ordinary Services. The tests that change them do it on the remote Workstation, so the other tests
 * keep this Workstation's.
 */
class FirstSetupComponentTest extends DashboardComponentTest {

  private static final List<String> OWN_SERVICES = List.of("yard", "consul-ui", "traefik-admin");

  static void assertCheck(JsonNode instance, String path) {
    JsonNode check = instance.get("check");
    assertThat(check.get("type").asString()).isEqualTo("http");
    assertThat(check.get("path").asString()).isEqualTo(path);
    assertThat(check.get("interval").asString()).isEqualTo("10s");
    assertThat(check.get("timeout").asString()).isEqualTo("2s");
  }

  @Test
  void yardIsTheDashboardWithAHealthCheckAndLocalScope() {
    ResponseEntity<JsonNode> response = get(servicePath("yard"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode yard = response.getBody();
    assertThat(yard.get("publication").get("scope").asString()).isEqualTo("local");
    assertThat(yard.get("hostname").asString()).isEqualTo("yard.lvh.test");
    assertThat(yard.get("instances")).hasSize(1);
    JsonNode instance = yard.get("instances").get(0);
    assertThat(instance.get("host").asString()).isEqualTo("127.0.0.1");
    assertThat(instance.get("port").asInt()).isEqualTo(port);
    assertCheck(instance, "/actuator/health");
  }

  @Test
  void theThreeServicesHaveNoAccessLog() {
    for (String service : List.of("yard", "consul-ui", "traefik-admin")) {
      assertThat(
              get(servicePath(service)).getBody().get("publication").get("accessLog").asBoolean())
          .as(service)
          .isFalse();
    }
  }

  @Test
  void servicesHaveNoCoreField() {
    JsonNode yard = get(servicePath("yard")).getBody();

    assertThat(yard.has("core")).isFalse();
    assertThat(yard.get("instances").get(0).has("core")).isFalse();
  }

  @Test
  void consulsOwnEntryIsNotInTheCatalog() {
    assertThat(get(servicePath("consul")).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    for (JsonNode service : get("/api/services").getBody()) {
      assertThat(service.get("name").asString()).isNotEqualTo("consul");
    }
  }

  @Test
  void servicePageOfYardShowsTheSameControlsAsAnyService() {
    String page = getPage("/services/" + WORKSTATION + "/yard").getBody();

    assertThat(page.substring(page.indexOf("<main")))
        .doesNotContain("Core Service")
        .contains("Unpublish")
        .contains("Share with the Yard")
        .contains("Instance Priority")
        .contains(">Remove<");
  }

  @Test
  void yardAndTraefikAreFreeNames() {
    String id =
        post(instancesPath("traefik"), Map.of("host", "10.1.2.3", "port", 8080))
            .getBody()
            .get("id")
            .asString();
    try {
      ResponseEntity<JsonNode> alias =
          put(
              "/api/workstation/aliases/yard",
              Map.of("workstation", RemoteWorkstation.NAME, "service", "traefik-admin"));

      assertThat(alias.getStatusCode().is2xxSuccessful()).isTrue();
    } finally {
      delete("/api/workstation/aliases/yard");
      delete(instancesPath("traefik") + "/" + id);
    }
  }
}
