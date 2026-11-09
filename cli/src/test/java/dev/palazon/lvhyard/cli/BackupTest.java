package dev.palazon.lvhyard.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BackupTest extends YardTestSupport {

  @Test
  void exportPrintsTheBackup() {
    dashboard.answer("GET", "/api/workstation/backup", 200, BACKUP);

    Run run = yard("export");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out()).isEqualTo(BACKUP);
  }

  @Test
  void exportWritesTheBackupToAFileThatOnlyTheUserCanRead(@TempDir Path dir) throws Exception {
    dashboard.answer("GET", "/api/workstation/backup", 200, BACKUP);
    Path file = dir.resolve("ws-a.yaml");

    Run run = yard("export", "-o", file.toString());

    assertThat(run.exitCode()).isZero();
    assertThat(run.out()).isEqualTo("Wrote the Backup to " + file + ".\n");
    assertThat(Files.readString(file)).isEqualTo(BACKUP);
    assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(file)))
        .isEqualTo("rw-------");
  }

  @Test
  void exportOverwritesAFileAndMakesItReadableOnlyByTheUser(@TempDir Path dir) throws Exception {
    dashboard.answer("GET", "/api/workstation/backup", 200, BACKUP);
    Path file = dir.resolve("ws-a.yaml");
    Files.writeString(
        file, "an older and longer Backup, with more text than the new one has in it\n".repeat(20));
    Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-r--r--"));

    Run run = yard("export", "--output", file.toString());

    assertThat(run.exitCode()).isZero();
    assertThat(Files.readString(file)).isEqualTo(BACKUP);
    assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(file)))
        .isEqualTo("rw-------");
  }

  @Test
  void importSendsTheFileAndPrintsEachPlanEntry(@TempDir Path dir) throws Exception {
    dashboard.answer("POST", "/api/workstation/backup?mode=add", 200, PLAN.formatted(false));
    Path file = dir.resolve("ws-a.yaml");
    Files.writeString(file, BACKUP);

    Run run = yard("import", file.toString());

    assertThat(run.exitCode()).isZero();
    assertThat(run.out()).isEqualTo(PLAN_LINES + "Added 2, skipped 2.\n");
    dashboard.verifyCalled("POST", "/api/workstation/backup", "application/yaml", BACKUP);
  }

  @Test
  void importWithDryRunAsksForThePlanOnly(@TempDir Path dir) throws Exception {
    dashboard.answer(
        "POST", "/api/workstation/backup?mode=add&dry-run=true", 200, PLAN.formatted(true));
    Path file = dir.resolve("ws-a.yaml");
    Files.writeString(file, BACKUP);

    Run run = yard("import", "--dry-run", file.toString());

    assertThat(run.exitCode()).isZero();
    assertThat(run.out()).isEqualTo(PLAN_LINES + "Dry run: nothing changed.\n");
  }

  @Test
  void importWithUpdateAsksToOverwriteAndCountsEachUpdate(@TempDir Path dir) throws Exception {
    dashboard.answer(
        "POST",
        "/api/workstation/backup?mode=update",
        200,
        """
        {"mode": "update", "dryRun": false,
         "entries": [
           {"kind": "service", "name": "web", "action": "skip", "reason": "It exists already."},
           {"kind": "static-instance", "name": "10.1.2.3:8080", "service": "web",
            "action": "update"},
           {"kind": "publication", "name": "web", "action": "update"},
           {"kind": "instance-priority", "name": "web", "action": "update"},
           {"kind": "alias", "name": "ws-b/grafana", "service": "dash", "action": "add"}]}
        """);
    Path file = dir.resolve("ws-a.yaml");
    Files.writeString(file, BACKUP);

    Run run = yard("import", "--update", file.toString());

    assertThat(run.exitCode()).isZero();
    assertThat(run.out())
        .isEqualTo(
            """
            skip Service web: It exists already.
            update Static Instance 10.1.2.3:8080 of web
            update Publication of web
            update Instance Priority of web
            add Alias to ws-b/grafana of dash
            Added 1, updated 3, skipped 1.
            """);
    dashboard.verifyCalled("POST", "/api/workstation/backup", "application/yaml", BACKUP);
  }

  @Test
  void importPrintsTheNameOfTheSafetyBackup(@TempDir Path dir) throws Exception {
    dashboard.answer(
        "POST",
        "/api/workstation/backup?mode=add",
        200,
        PLAN.formatted(false)
            .replace(
                "\"dryRun\": false,",
                "\"dryRun\": false,"
                    + " \"safetyBackup\": \"ws-a-20261105-100000-before-import.yaml\","));
    Path file = dir.resolve("ws-a.yaml");
    Files.writeString(file, BACKUP);

    Run run = yard("import", file.toString());

    assertThat(run.exitCode()).isZero();
    assertThat(run.out())
        .isEqualTo(
            PLAN_LINES
                + "Added 2, skipped 2.\n"
                + "Safety Backup: ws-a-20261105-100000-before-import.yaml, in the Backups"
                + " folder.\n");
  }

  @Test
  void importPrintsEachProblemWithItsPlaceWhenTheFileIsRefused(@TempDir Path dir) throws Exception {
    dashboard.answer(
        "POST",
        "/api/workstation/backup?mode=add",
        400,
        """
        {"title": "Bad Request", "status": 400, "detail": "The Backup is refused.",
         "problems": [
           {"place": "services.web.sources[0].static-instance.port", "reason": "The port is missing."},
           {"place": "", "reason": "The file is not valid YAML."}]}
        """);
    Path file = dir.resolve("ws-a.yaml");
    Files.writeString(file, BACKUP);

    Run run = yard("import", file.toString());

    assertThat(run.exitCode()).isEqualTo(1);
    assertThat(run.out()).isEmpty();
    assertThat(run.err())
        .isEqualTo(
            """
            services.web.sources[0].static-instance.port: The port is missing.
            The file is not valid YAML.
            """);
  }

  @Test
  void importThatStopsHalfwayListsWhatItAppliedAndTheSafetyBackup(@TempDir Path dir)
      throws Exception {
    dashboard.answer(
        "POST",
        "/api/workstation/backup?mode=add",
        500,
        """
        {"title": "Internal Server Error", "status": 500,
         "detail": "The import stopped at Publication of web: Consul does not answer.",
         "applied": [
           {"kind": "route-profile", "name": "gzip", "action": "add"},
           {"kind": "static-instance", "name": "10.1.2.3:8080", "service": "web",
            "action": "add"}],
         "safetyBackup": "ws-a-20261105-100000-before-import.yaml"}
        """);
    Path file = dir.resolve("ws-a.yaml");
    Files.writeString(file, BACKUP);

    Run run = yard("import", file.toString());

    assertThat(run.exitCode()).isEqualTo(1);
    assertThat(run.err())
        .isEqualTo(
            """
            The import stopped at Publication of web: Consul does not answer.
            It applied:
            add Route Profile gzip
            add Static Instance 10.1.2.3:8080 of web
            Safety Backup: ws-a-20261105-100000-before-import.yaml, in the Backups folder. \
            To go back, import it.
            """);
  }

  @Test
  void importThatStopsAtTheFirstChangeSaysItAppliedNothing(@TempDir Path dir) throws Exception {
    dashboard.answer(
        "POST",
        "/api/workstation/backup?mode=add",
        500,
        """
        {"title": "Internal Server Error", "status": 500,
         "detail": "The import stopped at Route Profile gzip: Consul does not answer.",
         "applied": [],
         "safetyBackup": "ws-a-20261105-100000-before-import.yaml"}
        """);
    Path file = dir.resolve("ws-a.yaml");
    Files.writeString(file, BACKUP);

    Run run = yard("import", file.toString());

    assertThat(run.exitCode()).isEqualTo(1);
    assertThat(run.err())
        .isEqualTo(
            """
            The import stopped at Route Profile gzip: Consul does not answer.
            It applied nothing.
            Safety Backup: ws-a-20261105-100000-before-import.yaml, in the Backups folder. \
            To go back, import it.
            """);
  }

  @Test
  void importOfAMissingFileFails(@TempDir Path dir) {
    Run run = yard("import", dir.resolve("nope.yaml").toString());

    assertThat(run.exitCode()).isEqualTo(1);
    assertThat(run.err()).startsWith("Cannot read " + dir.resolve("nope.yaml"));
    dashboard.verifyNotCalled();
  }

  @Test
  void importReadsStandardInputForADash() throws Exception {
    dashboard.answer("POST", "/api/workstation/backup?mode=add", 200, PLAN.formatted(false));

    Run run = yardWithInput(BACKUP, "--dashboard", dashboard.url().toString(), "import", "-");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out()).isEqualTo(PLAN_LINES + "Added 2, skipped 2.\n");
    dashboard.verifyCalled("POST", "/api/workstation/backup", "application/yaml", BACKUP);
  }
}
