package dev.palazon.lvhyard.dashboard;

import static dev.palazon.lvhyard.dashboard.BackupImportIT.entries;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/** An import in update mode: it also overwrites what exists with the values of the file. */
class BackupUpdateIT extends DashboardIT {

  static final String UPDATE_PATH = BackupExportIT.BACKUP_PATH + "?mode=update";

  ResponseEntity<JsonNode> update(String backup) {
    return postYaml(UPDATE_PATH, backup);
  }

  @Test
  void aliasGetsTheTargetOfTheFile() {
    String grafana = newServiceName("grafana");
    String loki = newServiceName("loki");
    for (String service : List.of(grafana, loki)) {
      remote.register(service, Map.of("host", "10.9.8.7", "port", 3000));
    }
    String dash = newServiceName("dash");
    put(
        "/api/workstation/aliases/" + dash,
        Map.of("workstation", RemoteWorkstation.NAME, "service", grafana));

    ResponseEntity<JsonNode> response =
        update(
            """
            version: 1
            aliases:
              %s:
                workstation: %s
                service: %s
            """
                .formatted(dash, RemoteWorkstation.NAME, loki));

    assertThat(entries(response)).containsExactly("update alias " + dash);
    JsonNode alias =
        get("/api/workstation/aliases")
            .getBody()
            .valueStream()
            .filter(candidate -> candidate.get("name").asString().equals(dash))
            .findFirst()
            .orElseThrow();
    assertThat(alias.findValuesAsString("service")).contains(loki);
  }

  @Test
  void aliasOverwriteToAMissingRemoteServiceIsApplied() {
    String grafana = newServiceName("grafana");
    remote.register(grafana, Map.of("host", "10.9.8.7", "port", 3000));
    String dash = newServiceName("dash");
    put(
        "/api/workstation/aliases/" + dash,
        Map.of("workstation", RemoteWorkstation.NAME, "service", grafana));

    ResponseEntity<JsonNode> response =
        update(
            """
            version: 1
            aliases:
              %s:
                workstation: %s
                service: nothing-here
            """
                .formatted(dash, RemoteWorkstation.NAME));

    assertThat(entries(response)).containsExactly("update alias " + dash);
  }
}
