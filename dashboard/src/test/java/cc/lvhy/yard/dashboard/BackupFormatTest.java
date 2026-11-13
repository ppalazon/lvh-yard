package cc.lvhy.yard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import cc.lvhy.yard.dashboard.backups.Backup;
import cc.lvhy.yard.dashboard.backups.BackupFormat;
import cc.lvhy.yard.dashboard.backups.BackupProblem;
import cc.lvhy.yard.dashboard.backups.RefusedBackupException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/** The YAML of a Backup file: what an export writes and an import reads. */
class BackupFormatTest {

  private static final String BACKUP =
      """
      version: 3
      exported-from:
        workstation: ws-a
        at: '2026-11-05T10:00:00Z'
      route-profiles:
        auth:
          basicAuth:
            users:
              - me:$apr1$x$y
        certificate:
          clientCertificate: {}
      services:
        grafana:
          publication:
            scope: yard
            route-profiles:
              - auth
            access-log: true
          instance-priority: false
          sources:
            - alias:
                workstation: ws-b
                service: grafana
            - static-instance:
                host: 10.1.2.3
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
            - container-binding:
                container: web-1
                port: 80
            - static-instance:
                port: 3001
            - container-binding:
                compose:
                  project: shop
                  service: web
                port: 8080
            - alias:
                workstation: ws-c
                service: grafana
        notes:
          publication: none
          instance-priority: true
        db: {}
      """;

  @Test
  void writeGivesBackWhatReadReads() {
    Backup backup = BackupFormat.read(BACKUP);

    assertThat(BackupFormat.read(BackupFormat.write(backup))).isEqualTo(backup);
    assertThat(BackupFormat.write(backup)).isEqualTo(BACKUP);
  }

  @Test
  void versionTwoStillReadsAndWritesTheCurrentFormat() {
    Backup backup = BackupFormat.read("version: 2\nroute-profiles:\n  zip:\n    compress: {}\n");

    assertThat(backup.routeProfiles()).containsKey("zip");
    assertThat(BackupFormat.write(backup)).startsWith("version: 3\n");
  }

  @Test
  void theSourcesKeepTheirOrder() {
    Backup.Service grafana = BackupFormat.read(BACKUP).services().get("grafana");

    assertThat(grafana.sources())
        .extracting(source -> source.getClass().getSimpleName())
        .containsExactly(
            "Alias",
            "StaticInstance",
            "ContainerBinding",
            "StaticInstance",
            "ContainerBinding",
            "Alias");
    assertThat(grafana.staticInstances())
        .extracting(Backup.StaticInstance::port)
        .containsExactly(3000, 3001);
    assertThat(grafana.containerBindings())
        .extracting(Backup.ContainerBinding::id)
        .containsExactly("web-1:80", "compose:shop:web:8080");
    assertThat(grafana.aliases())
        .extracting(Backup.Alias::id)
        .containsExactly("ws-b/grafana", "ws-c/grafana");
  }

  @Test
  void readKeepsWhatTheFileLeavesOut() {
    Backup backup = BackupFormat.read(BACKUP);

    assertThat(backup.services().get("db").publication()).isNull();
    assertThat(backup.services().get("db").instancePriority()).isNull();
    assertThat(backup.services().get("db").sources()).isEmpty();
    assertThat(backup.services().get("notes").publication()).isEqualTo(new Backup.Unpublished());
    assertThat(backup.services().get("notes").instancePriority()).isTrue();
    assertThat(backup.services().get("grafana").instancePriority()).isFalse();
    assertThat(BackupFormat.read(BACKUP.replace("      access-log: true\n", "")).services())
        .extractingByKey("grafana")
        .extracting(service -> ((Backup.Published) service.publication()).accessLog())
        .isEqualTo(false);
    assertThat(backup.services().get("grafana").staticInstances().get(1).host()).isNull();
  }

  @Test
  void unknownKeysAndWrongValuesAreRefusedEachWithItsPlace() {
    RefusedBackupException refused =
        catchThrowableOfType(
            RefusedBackupException.class,
            () ->
                BackupFormat.read(
                    """
                    version: 2
                    extra: true
                    services:
                      grafana:
                        instance-priority: maybe
                        sources:
                          - static-instance:
                              host: 10.1.2.3
                              prot: 3000
                          - foreign-instance:
                              port: 80
                          - alias:
                              workstation: ws-b
                              service: grafana
                            container-binding:
                              container: web-1
                              port: 80
                    """));

    assertThat(refused.problems())
        .extracting(BackupProblem::place)
        .containsExactly(
            "extra",
            "services.grafana.instance-priority",
            "services.grafana.sources[0].static-instance.prot",
            "services.grafana.sources[0].static-instance.port",
            "services.grafana.sources[1]",
            "services.grafana.sources[2]");
  }

  @Test
  void anOlderVersionIsRefusedWithItsReason() {
    RefusedBackupException refused =
        catchThrowableOfType(
            RefusedBackupException.class, () -> BackupFormat.read("version: 1\nservices: {}\n"));

    assertThat(refused.problems())
        .containsExactly(
            new BackupProblem(
                "version",
                "The version 1 is an older Backup format. This dashboard reads versions 2 and 3."));
  }

  @Test
  void anotherVersionIsRefused() {
    RefusedBackupException refused =
        catchThrowableOfType(
            RefusedBackupException.class, () -> BackupFormat.read("version: 4\nservices: {}\n"));

    assertThat(refused.problems())
        .containsExactly(
            new BackupProblem(
                "version",
                "The version 4 is not one this dashboard reads. It reads versions 2 and 3."));
  }

  @Test
  void textThatIsNotYamlIsRefused() {
    RefusedBackupException refused =
        catchThrowableOfType(RefusedBackupException.class, () -> BackupFormat.read("a: [b"));

    assertThat(refused.problems()).extracting(BackupProblem::place).containsExactly("");
    assertThat(List.of(refused.getMessage()))
        .allMatch(message -> message.contains("not valid YAML"));
  }

  @Test
  void aBackupMustBeAMapping() {
    RefusedBackupException refused =
        catchThrowableOfType(RefusedBackupException.class, () -> BackupFormat.read("[]"));

    assertThat(refused.problems()).extracting(BackupProblem::place).containsExactly("");
  }

  @ParameterizedTest
  @MethodSource("wrongShapes")
  void wrongShapesAreRefusedAtTheirPlace(String yaml, String place) {
    RefusedBackupException refused =
        catchThrowableOfType(
            RefusedBackupException.class, () -> BackupFormat.read("version: 3\n" + yaml));

    assertThat(refused.problems()).extracting(BackupProblem::place).containsExactly(place);
  }

  static Stream<Arguments> wrongShapes() {
    return Stream.of(
        Arguments.of("exported-from: {at: yesterday}", "exported-from.at"),
        Arguments.of("route-profiles: {auth: []}", "route-profiles.auth"),
        Arguments.of("services: []", "services"),
        Arguments.of("services: {web: {publication: true}}", "services.web.publication"),
        Arguments.of("services: {web: {sources: {}}}", "services.web.sources"),
        Arguments.of("services: {web: {sources: [null]}}", "services.web.sources[0]"),
        Arguments.of(
            "services: {web: {sources: [{static-instance: {port: tcp}}]}}",
            "services.web.sources[0].static-instance.port"),
        Arguments.of(
            "services: {web: {sources: [{static-instance: {port: 80, host: []}}]}}",
            "services.web.sources[0].static-instance.host"),
        Arguments.of(
            "services: {web: {sources: [{container-binding: {port: 80}}]}}",
            "services.web.sources[0].container-binding"),
        Arguments.of(
            "services: {web: {sources: [{container-binding: {port: 80, container: web, compose:"
                + " {project: shop, service: web}}}]}}",
            "services.web.sources[0].container-binding"),
        Arguments.of(
            "services: {web: {sources: [{alias: {workstation: ws-b}}]}}",
            "services.web.sources[0].alias.service"));
  }

  @Test
  void aMissingVersionIsRefused() {
    RefusedBackupException refused =
        catchThrowableOfType(RefusedBackupException.class, () -> BackupFormat.read("services: {}"));

    assertThat(refused.problems()).extracting(BackupProblem::place).containsExactly("version");
  }

  @Test
  void numericAndBooleanTagsAndMetaReadAsText() {
    Backup.StaticInstance instance =
        BackupFormat.read(
                """
                version: 3
                services:
                  web:
                    sources:
                      - static-instance:
                          port: 80
                          tags: [42, true]
                          meta: {build: 42, ready: true, empty: null}
                """)
            .services()
            .get("web")
            .staticInstances()
            .getFirst();

    assertThat(instance.tags()).containsExactly("42", "true");
    assertThat(instance.meta())
        .isEqualTo(java.util.Map.of("build", "42", "ready", "true", "empty", ""));
  }

  @ParameterizedTest
  @ValueSource(strings = {"2026-11-05T10:00:00Z", "'2026-11-05T12:00:00+02:00'"})
  void quotedAndYamlTimestampsKeepTheExportTime(String at) {
    Backup backup =
        BackupFormat.read("version: 3\nexported-from: {workstation: ws-a, at: " + at + "}");

    assertThat(backup.exportedFrom().at().toInstant())
        .isEqualTo(OffsetDateTime.parse("2026-11-05T10:00:00Z").toInstant());
    assertThat(BackupFormat.read(BackupFormat.write(backup))).isEqualTo(backup);
  }

  @Test
  void anExportWithoutATimeKeepsItsWorkstation() {
    Backup backup = BackupFormat.read("version: 3\nexported-from: {workstation: ws-a}");

    assertThat(backup.exportedFrom()).isEqualTo(new Backup.ExportedFrom("ws-a", null));
    assertThat(BackupFormat.read(BackupFormat.write(backup))).isEqualTo(backup);
  }

  @Test
  void anEmptyServiceNeedsNoOptions() {
    Backup backup = BackupFormat.read("version: 3\nservices: {web: null}");

    assertThat(backup.services().get("web")).isEqualTo(new Backup.Service(null, null, List.of()));
    assertThat(BackupFormat.read(BackupFormat.write(backup))).isEqualTo(backup);
  }
}
