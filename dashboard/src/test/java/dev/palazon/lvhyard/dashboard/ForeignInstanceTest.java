package dev.palazon.lvhyard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/** An Instance that another tool registered straight in Consul. */
class ForeignInstanceTest extends DashboardTest {

  private String service;
  private String id;

  private RestClient consul() {
    return RestClient.create(
        "http://"
            + consulContainer.getHost()
            + ":"
            + consulContainer.getMappedPort(TestcontainersConfiguration.CONSUL_HTTP_PORT));
  }

  @BeforeEach
  void registerStraightInConsul() {
    service = newServiceName("foreign");
    id = "foreign-" + UUID.randomUUID();
    consul()
        .put()
        .uri("/v1/agent/service/register")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("ID", id, "Name", service, "Address", "10.1.2.3", "Port", 8080))
        .retrieve()
        .toBodilessEntity();
  }

  @AfterEach
  void deregister() {
    consul().put().uri("/v1/agent/service/deregister/" + id).retrieve().toBodilessEntity();
  }

  @Test
  void apiMarksItForeignAndWarnsWithTheCommandThatRemovesIt() {
    JsonNode found = get(servicePath(service)).getBody();

    assertThat(found.get("state").asString()).isEqualTo("unpublished");
    assertThat(found.get("instances").get(0).get("foreign").asBoolean()).isTrue();
    JsonNode warning = found.get("warnings").get(0);
    assertThat(warning.get("kind").asString()).isEqualTo("foreign-instance");
    assertThat(warning.get("severity").asString()).isEqualTo("risk");
    assertThat(warning.get("message").asString())
        .contains("10.1.2.3:8080")
        .contains("docker compose exec consul consul services deregister -id=" + id);
  }

  @Test
  void staticInstanceIsNotForeign() {
    String grafana = newServiceName("grafana");
    register(grafana, Map.of("host", "10.1.2.3", "port", 3000));

    JsonNode found = get(servicePath(grafana)).getBody();

    assertThat(found.get("instances").get(0).has("foreign")).isFalse();
    assertThat(found.get("warnings").valueStream())
        .noneMatch(warning -> warning.get("kind").asString().equals("foreign-instance"));
  }

  @Test
  void servicePageShowsTheRiskAndNoEditOrRemove() {
    String page = getPage("/services/ws-test/" + service).getBody();

    assertThat(page)
        .contains("id=\"risks\"")
        .contains("consul services deregister -id=" + id)
        .doesNotContain("/instances/" + id + "/edit")
        .doesNotContain("/instances/" + id + "/remove");
  }

  @Test
  void staticInstancesPageDoesNotListIt() {
    assertThat(getPage("/static-instances").getBody()).doesNotContain(service);
  }

  @Test
  void backupDoesNotHoldIt() {
    Map<String, Object> backup =
        BackupExportTest.yaml(getYaml(BackupExportTest.BACKUP_PATH).getBody());

    // The Backup names every Service, but an import must not register the Instance.
    assertThat(BackupExportTest.map(BackupExportTest.map(backup.get("services")).get(service)))
        .doesNotContainKey("static-instances");
  }
}
