package dev.palazon.lvhyard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import dev.palazon.lvhyard.dashboard.backups.Backup;
import dev.palazon.lvhyard.dashboard.backups.BackupFormat;
import dev.palazon.lvhyard.dashboard.backups.BackupProblem;
import dev.palazon.lvhyard.dashboard.backups.RefusedBackupException;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The YAML of a Backup file, version 1: what an export writes and an import reads. */
class BackupFormatTest {

  private static final String BACKUP =
      """
      version: 1
      exported-from:
        workstation: ws-a
        at: '2026-11-05T10:00:00Z'
      route-profiles:
        auth:
          basicAuth:
            users:
              - me:$apr1$x$y
      services:
        grafana:
          publication:
            scope: yard
            priority:
              - static
              - bound
            route-profiles:
              - auth
          static-instances:
            - host: 10.1.2.3
              port: 3000
              check:
                type: http
                path: /health
                interval: 5s
                timeout: 2s
              tags:
                - blue
              meta:
                version: '2'
            - port: 3001
          container-bindings:
            - container: web-1
              port: 80
            - compose:
                project: shop
                service: web
              port: 8080
        notes:
          publication: none
        db: {}
      aliases:
        pg:
          workstation: ws-b
          service: postgres
      """;

  @Test
  void writeGivesBackWhatReadReads() {
    Backup backup = BackupFormat.read(BACKUP);

    assertThat(BackupFormat.read(BackupFormat.write(backup))).isEqualTo(backup);
    assertThat(BackupFormat.write(backup)).isEqualTo(BACKUP);
  }

  @Test
  void readKeepsWhatTheFileLeavesOut() {
    Backup backup = BackupFormat.read(BACKUP);

    assertThat(backup.services().get("db").publication()).isNull();
    assertThat(backup.services().get("notes").publication()).isEqualTo(new Backup.Unpublished());
    assertThat(backup.services().get("grafana").staticInstances().get(1).host()).isNull();
    assertThat(backup.services().get("grafana").containerBindings())
        .extracting(Backup.ContainerBinding::id)
        .containsExactly("web-1:80", "compose:shop:web:8080");
  }

  @Test
  void unknownKeysAndWrongValuesAreRefusedEachWithItsPlace() {
    RefusedBackupException refused =
        catchThrowableOfType(
            RefusedBackupException.class,
            () ->
                BackupFormat.read(
                    """
                    version: 1
                    extra: true
                    services:
                      grafana:
                        static-instances:
                          - host: 10.1.2.3
                            prot: 3000
                    """));

    assertThat(refused.problems())
        .extracting(BackupProblem::place)
        .containsExactly(
            "extra",
            "services.grafana.static-instances[0].prot",
            "services.grafana.static-instances[0].port");
  }

  @Test
  void anotherVersionIsRefused() {
    RefusedBackupException refused =
        catchThrowableOfType(
            RefusedBackupException.class, () -> BackupFormat.read("version: 2\nservices: {}\n"));

    assertThat(refused.problems())
        .containsExactly(
            new BackupProblem(
                "version", "The version 2 is not one this dashboard reads. It reads version 1."));
  }

  @Test
  void textThatIsNotYamlIsRefused() {
    RefusedBackupException refused =
        catchThrowableOfType(RefusedBackupException.class, () -> BackupFormat.read("a: [b"));

    assertThat(refused.problems()).extracting(BackupProblem::place).containsExactly("");
    assertThat(List.of(refused.getMessage()))
        .allMatch(message -> message.contains("not valid YAML"));
  }
}
