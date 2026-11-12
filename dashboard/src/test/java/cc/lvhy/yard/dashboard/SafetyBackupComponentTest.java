package cc.lvhy.yard.dashboard;

import static cc.lvhy.yard.dashboard.BackupExportComponentTest.map;
import static cc.lvhy.yard.dashboard.BackupExportComponentTest.yaml;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/** Each import that changes something first writes a safety Backup to the Backups folder. */
class SafetyBackupComponentTest extends DashboardComponentTest {

  @Test
  void importThatChangesSomethingWritesASafetyBackupOfTheSetupBefore() throws IOException {
    String grafana = newServiceName("grafana");
    String loki = newServiceName("loki");
    register(grafana, Map.of("host", "10.1.2.3", "port", 3000));
    String certificate = newServiceName("certificate");
    putRouteProfiles(Map.of(certificate, Map.of("clientCertificate", Map.of())));

    ResponseEntity<JsonNode> response =
        postYaml(
            BackupImportComponentTest.IMPORT_PATH,
            """
            version: 2
            services:
              %s:
                sources:
                  - static-instance:
                      host: 10.1.2.4
                      port: 3100
            """
                .formatted(loki));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    String name = response.getBody().get("safetyBackup").asString();
    assertThat(name).matches(WORKSTATION + "-\\d{8}-\\d{6}(-\\d+)?-before-import\\.yaml");
    Path file = BACKUPS.resolve(name);
    assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(file)))
        .isEqualTo("rw-------");
    Map<String, Object> backup = yaml(Files.readString(file));
    assertThat(backup.get("version")).isEqualTo(3);
    assertThat(map(backup.get("route-profiles")).get(certificate))
        .isEqualTo(Map.of("clientCertificate", Map.of()));
    Map<String, Object> services = map(backup.get("services"));
    assertThat(services).containsKey(grafana).doesNotContainKey(loki);
  }

  @Test
  void importThatChangesNothingWritesNoSafetyBackup() throws IOException {
    String grafana = newServiceName("grafana");
    register(grafana, Map.of("host", "10.1.2.3", "port", 3000));
    String backup =
        """
        version: 2
        services:
          %s:
            sources:
              - static-instance:
                  host: 10.1.2.3
                  port: 3000
        """
            .formatted(grafana);

    long before = backupCount();
    ResponseEntity<JsonNode> response = postYaml(BackupImportComponentTest.IMPORT_PATH, backup);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().has("safetyBackup")).isFalse();
    assertThat(backupCount()).isEqualTo(before);
  }

  @Test
  void dryRunWritesNoSafetyBackup() throws IOException {
    long before = backupCount();
    String loki = newServiceName("loki");

    ResponseEntity<JsonNode> response =
        postYaml(
            BackupImportComponentTest.IMPORT_PATH + "&dry-run=true",
            """
            version: 2
            services:
              %s:
                sources:
                  - static-instance:
                      host: 10.1.2.4
                      port: 3100
            """
                .formatted(loki));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().has("safetyBackup")).isFalse();
    assertThat(backupCount()).isEqualTo(before);
  }

  /** The tests of one JVM run one at a time and share its Backups folder. */
  private static long backupCount() throws IOException {
    try (var files = Files.list(BACKUPS)) {
      return files.count();
    }
  }
}
