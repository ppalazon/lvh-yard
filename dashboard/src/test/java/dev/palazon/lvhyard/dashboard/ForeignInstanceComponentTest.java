package dev.palazon.lvhyard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import dev.palazon.lvhyard.dashboard.consul.Consul.Registration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** An Instance that another tool registered straight in Consul. */
class ForeignInstanceComponentTest extends DashboardComponentTest {

  private String service;
  private String id;

  @BeforeEach
  void registerStraightInConsul() {
    service = newServiceName("foreign");
    id = "foreign-" + UUID.randomUUID();
    consul.register(new Registration(id, service, "10.1.2.3", 8080, List.of(), Map.of(), null));
  }

  @AfterEach
  void deregister() {
    consul.deregister(id);
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
        .contains("PUT /v1/agent/service/deregister/" + id);
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
        .contains("/v1/agent/service/deregister/" + id)
        .doesNotContain("/instances/" + id + "/edit")
        .doesNotContain("/instances/" + id + "/remove");
  }

  @Test
  void backupDoesNotHoldIt() {
    assertThat(backedUp()).doesNotContainKey(service);

    register(service, Map.of("host", "10.1.2.4", "port", 3000));

    assertThat(BackupExportComponentTest.map(backedUp().get(service)).get("sources"))
        .isEqualTo(
            List.of(
                Map.of(
                    "static-instance",
                    Map.of(
                        "host",
                        "10.1.2.4",
                        "port",
                        3000,
                        "check",
                        Map.of("type", "tcp", "interval", "10s", "timeout", "2s")))));
  }

  private Map<String, Object> backedUp() {
    return BackupExportComponentTest.map(
        BackupExportComponentTest.yaml(getYaml(BackupExportComponentTest.BACKUP_PATH).getBody())
            .get("services"));
  }
}
