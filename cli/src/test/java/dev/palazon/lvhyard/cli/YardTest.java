package dev.palazon.lvhyard.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The {@code lvh-yard} command as a shell runs it: arguments in, exit code and output out. Behind
 * it is a stub dashboard that answers only what {@code openapi.yaml} describes.
 */
class YardTest {

  /**
   * The native executable to test in place of the classes, as {@code -Pnative} builds it: every
   * test then runs it in a process of its own. Unset, the tests run the classes in this JVM.
   */
  private static final String NATIVE_YARD = System.getProperty("yard.executable");

  private final StubDashboard dashboard = new StubDashboard();

  /**
   * The HOME of every {@link #yardProcess}, empty unless a test writes to it, so no test reads the
   * real user's {@code ~/.config/lvh-yard/.env}.
   */
  @TempDir Path home;

  @AfterEach
  void stopDashboard() {
    dashboard.close();
  }

  @Test
  void withoutSubcommandShowsUsage() {
    Run run = yard();

    assertThat(run.exitCode()).isZero();
    assertThat(run.out()).contains("Usage: lvh-yard").contains("--dashboard=<url>");
  }

  @Test
  void versionNamesThisBuild() {
    Run run = yard("--version");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out()).isEqualTo("lvh-yard " + Version.VERSION + "\n");
    assertThat(Version.VERSION).matches("\\d+\\.\\d+\\.\\d+(-.+)?");
  }

  @Test
  void everySubcommandNamesTheVersionToo() {
    Run run = yard("alias", "add", "-V");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out()).isEqualTo("lvh-yard " + Version.VERSION + "\n");
  }

  @Test
  void listShowsThisWorkstationThenTheOthersWithTheirStateHostnameInstancesAndHealth() {
    dashboard.answer(
        "GET",
        "/api/yard",
        200,
        """
        {"home": {"name": "ws-a", "services": [
           {"workstation": "ws-a", "name": "api", "state": "unavailable", "publication": {"scope": "local"},
            "hostname": "api.lvh.me", "containerBindings": [], "warnings": [], "instances": []},
           {"workstation": "ws-a", "name": "grafana", "state": "healthy", "publication": {"scope": "local"},
            "hostname": "grafana.lvh.me", "containerBindings": [], "warnings": [], "instances": [
              {"id": "3f2a", "host": "10.1.2.3", "port": 3000, "health": "passing",
               "tags": [], "meta": {}, "checks": [], "outOfRange": false}]},
           {"workstation": "ws-a", "name": "web", "state": "unpublished", "containerBindings": [],
            "warnings": [], "instances": [
              {"id": "7b1c", "host": "10.1.2.3", "port": 8080, "health": "critical",
               "tags": [], "meta": {}, "checks": [], "outOfRange": false},
              {"id": "9d4e", "host": "10.1.2.3", "port": 8081, "health": "warning",
               "tags": [], "meta": {}, "checks": [], "outOfRange": false}]}]},
         "remotes": [
           {"name": "ws-b", "unreachable": false, "services": [
             {"workstation": "ws-b", "name": "postgres", "state": "unhealthy",
              "publication": {"scope": "local"}, "containerBindings": [], "warnings": [], "instances": [
                {"id": "5e6f", "host": "10.1.2.4", "port": 5432, "health": "critical",
                 "tags": [], "meta": {}, "checks": [], "outOfRange": false}]}]},
           {"name": "ws-c", "unreachable": true, "services": []},
           {"name": "ws-d", "unreachable": false, "services": []}]}
        """);

    Run run = yard("list");

    assertThat(run.exitCode()).isZero();
    assertThat(run.lines())
        .containsExactly(
            "ws-a (this Workstation)",
            "  api: unavailable at api.lvh.me",
            "  grafana: healthy at grafana.lvh.me",
            "    10.1.2.3:3000  passing   3f2a",
            "  web: unpublished",
            "    10.1.2.3:8080  critical  7b1c",
            "    10.1.2.3:8081  warning   9d4e",
            "ws-b",
            "  postgres: unhealthy",
            "    10.1.2.4:5432  critical  5e6f",
            "ws-c: Workstation unreachable",
            "ws-d: no Services");
  }

  @Test
  void listWithWorkstationShowsThatWorkstationsServices() {
    dashboard.answer(
        "GET",
        "/api/services?workstation=ws-b",
        200,
        """
        [{"workstation": "ws-b", "name": "postgres", "state": "unpublished", "containerBindings": [],
         "warnings": [], "instances": [
            {"id": "5e6f", "host": "10.1.2.4", "port": 5432, "health": "passing",
             "tags": [], "meta": {}, "checks": [], "outOfRange": false}]}]
        """);

    Run run = yard("list", "--workstation", "ws-b");

    assertThat(run.exitCode()).isZero();
    assertThat(run.lines())
        .containsExactly("ws-b", "  postgres: unpublished", "    10.1.2.4:5432  passing   5e6f");
  }

  @Test
  void listMarksTheInstancesTheRouteSendsRequestsTo() {
    dashboard.answer(
        "GET",
        "/api/services?workstation=ws-a",
        200,
        """
        [{"workstation": "ws-a", "name": "web", "state": "healthy", "publication": {"scope": "local"},
          "hostname": "web.lvh.me", "containerBindings": [], "warnings": [],
          "priority": ["static", "bound", "alias"],
          "routing": {"kind": "static", "instances": ["1a2b"]},
          "instances": [
            {"id": "1a2b", "host": "10.1.2.3", "port": 3000, "health": "passing",
             "tags": [], "meta": {}, "checks": [], "outOfRange": false},
            {"id": "web:abc:80", "host": "172.17.0.2", "port": 80, "health": "passing",
             "tags": [], "meta": {}, "checks": [], "outOfRange": false,
             "containerBinding": "abc:80"}]}]
        """);

    Run run = yard("list", "--workstation", "ws-a");

    assertThat(run.lines())
        .containsExactly(
            "ws-a",
            "  web: healthy at web.lvh.me",
            "    10.1.2.3:3000  passing   1a2b  routed",
            "    172.17.0.2:80  passing   web:abc:80");
  }

  @Test
  void listMarksAnInstanceOutOfRange() {
    dashboard.answer(
        "GET",
        "/api/services?workstation=ws-b",
        200,
        """
        [{"workstation": "ws-b", "name": "api", "state": "healthy", "publication": {"scope": "yard"},
          "hostname": "api.ws-b.lvh.me", "containerBindings": [],
           "warnings": [], "instances": [
            {"id": "1a2b", "host": "10.1.2.4", "port": 9090, "health": "passing",
             "tags": [], "meta": {}, "checks": [], "outOfRange": false},
            {"id": "3c4d", "host": "127.0.0.1", "port": 9091, "health": "passing",
             "tags": [], "meta": {}, "checks": [], "outOfRange": true}]}]
        """);

    Run run = yard("list", "--workstation", "ws-b");

    assertThat(run.lines())
        .containsExactly(
            "ws-b",
            "  api: healthy at api.ws-b.lvh.me",
            "    10.1.2.4:9090  passing   1a2b",
            "    127.0.0.1:9091  passing   3c4d  out of range");
  }

  @Test
  void listShowsTheServicesWarnings() {
    dashboard.answer(
        "GET",
        "/api/services?workstation=ws-b",
        200,
        """
        [{"workstation": "ws-b", "name": "api", "state": "healthy", "publication": {"scope": "yard"},
          "hostname": "api.ws-b.lvh.me", "containerBindings": [], "profiles": ["team-login"],
          "warnings": [{"kind": "unknown-route-profile",
                        "message": "The Route Profile team-login does not exist."}],
          "instances": [
            {"id": "1a2b", "host": "10.1.2.4", "port": 9090, "health": "passing",
             "tags": [], "meta": {}, "checks": [], "outOfRange": false}]}]
        """);

    Run run = yard("list", "--workstation", "ws-b");

    assertThat(run.lines())
        .containsExactly(
            "ws-b",
            "  api: healthy at api.ws-b.lvh.me",
            "    10.1.2.4:9090  passing   1a2b",
            "    Warning: The Route Profile team-login does not exist.");
  }

  @Test
  void listShowsTheServicesOfTheFirstSetupAsOrdinaryServices() {
    dashboard.answer(
        "GET",
        "/api/services?workstation=ws-b",
        200,
        """
        [{"workstation": "ws-b", "name": "consul-ui", "state": "healthy",
          "publication": {"scope": "local"}, "hostname": "consul-ui.lvh.me", "containerBindings": [],
          "warnings": [], "instances": [
            {"id": "consul-ui", "host": "127.0.0.1", "port": 8500, "health": "passing",
             "tags": [], "meta": {}, "checks": [], "outOfRange": false}]},
         {"workstation": "ws-b", "name": "traefik-admin", "state": "healthy",
          "publication": {"scope": "local"}, "hostname": "traefik-admin.lvh.me", "containerBindings": [],
          "warnings": [], "instances": [
            {"id": "traefik-admin", "host": "127.0.0.1", "port": 8080, "health": "passing",
             "tags": [], "meta": {}, "checks": [], "outOfRange": false}]},
         {"workstation": "ws-b", "name": "yard", "state": "healthy",
          "publication": {"scope": "local"}, "hostname": "yard.lvh.me", "containerBindings": [],
          "warnings": [], "instances": [
            {"id": "yard", "host": "127.0.0.1", "port": 7780, "health": "passing",
             "tags": [], "meta": {}, "checks": [], "outOfRange": false}]}]
        """);

    Run run = yard("list", "--workstation", "ws-b");

    assertThat(run.lines())
        .containsExactly(
            "ws-b",
            "  consul-ui: healthy at consul-ui.lvh.me",
            "    127.0.0.1:8500  passing   consul-ui",
            "  traefik-admin: healthy at traefik-admin.lvh.me",
            "    127.0.0.1:8080  passing   traefik-admin",
            "  yard: healthy at yard.lvh.me",
            "    127.0.0.1:7780  passing   yard");
  }

  @Test
  void listWithoutServicesSaysSo() {
    dashboard.answer(
        "GET",
        "/api/yard",
        200,
        """
        {"home": {"name": "ws-a", "services": []}, "remotes": []}
        """);
    dashboard.answer("GET", "/api/services?workstation=ws-b", 200, "[]");

    assertThat(yard("list").out()).isEqualTo("ws-a (this Workstation): no Services\n");
    assertThat(yard("list", "--workstation", "ws-b").out()).isEqualTo("ws-b: no Services\n");
  }

  @Test
  void listOfWorkstationNotInTheYardFailsWithTheReason() {
    dashboard.answer(
        "GET",
        "/api/services?workstation=ws-typo",
        404,
        """
        {"status": 404, "detail": "The Yard has no Workstation ws-typo."}
        """);

    Run run = yard("list", "--workstation", "ws-typo");

    assertThat(run.exitCode()).isNotZero();
    assertThat(run.out()).isEmpty();
    assertThat(run.err()).contains("The Yard has no Workstation ws-typo.");
  }

  @Test
  void listOfUnreachableWorkstationFailsWithTheReason() {
    dashboard.answer(
        "GET",
        "/api/services?workstation=ws-b",
        503,
        """
        {"status": 503, "detail": "Workstation ws-b is unreachable: its Consul datacenter does not answer."}
        """);

    Run run = yard("list", "--workstation", "ws-b");

    assertThat(run.exitCode()).isNotZero();
    assertThat(run.err())
        .contains("Workstation ws-b is unreachable: its Consul datacenter does not answer.");
  }

  @Test
  void registerStaticAddsStaticInstanceAtHostAndPort() {
    dashboard.answer(
        "POST",
        "/api/workstation/services/web/instances",
        201,
        """
        {"id": "7b1c", "host": "10.1.2.3", "port": 8080, "health": "critical",
         "tags": [], "meta": {}, "checks": [], "outOfRange": false}
        """);

    Run run = yard("register", "static", "web", "--host", "10.1.2.3", "--port", "8080");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out()).isEqualTo("Registered web at 10.1.2.3:8080, Instance 7b1c.\n");
    dashboard.verifyCalled(
        "POST",
        "/api/workstation/services/web/instances",
        """
        {"host": "10.1.2.3", "port": 8080}
        """);
  }

  @Test
  void registerStaticWithoutHostLeavesItToTheDashboard() {
    dashboard.answer(
        "POST",
        "/api/workstation/services/web/instances",
        201,
        """
        {"id": "7b1c", "host": "192.168.1.20", "port": 8080, "health": "critical",
         "tags": [], "meta": {}, "checks": [], "outOfRange": false}
        """);

    Run run = yard("register", "static", "web", "--port", "8080");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out()).contains("at 192.168.1.20:8080");
    dashboard.verifyCalled(
        "POST",
        "/api/workstation/services/web/instances",
        """
        {"port": 8080}
        """);
  }

  @Test
  void registerStaticTakesTheCheckItsIntervalAndTimeoutTagsAndMeta() {
    dashboard.answer(
        "POST",
        "/api/workstation/services/web/instances",
        201,
        """
        {"id": "7b1c", "host": "10.1.2.3", "port": 8080, "health": "critical",
         "check": {"type": "http", "path": "/health", "interval": "5s", "timeout": "1s"},
         "tags": ["backend", "v2"], "meta": {"owner": "pablo", "team": "core"},
         "checks": [{"name": "HTTP GET /health", "health": "critical", "output": ""}],
         "outOfRange": false}
        """);

    Run run =
        yard(
            "register",
            "static",
            "web",
            "--port",
            "8080",
            "--check",
            "http",
            "--path",
            "/health",
            "--interval",
            "5s",
            "--timeout",
            "1s",
            "--tag",
            "backend",
            "--tag",
            "v2",
            "--meta",
            "owner=pablo",
            "--meta",
            "team=core");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out()).isEqualTo("Registered web at 10.1.2.3:8080, Instance 7b1c.\n");
    dashboard.verifyCalled(
        "POST",
        "/api/workstation/services/web/instances",
        """
        {"port": 8080, "check": {"type": "http", "path": "/health", "interval": "5s", "timeout": "1s"},
         "tags": ["backend", "v2"], "meta": {"owner": "pablo", "team": "core"}}
        """);
  }

  @Test
  void registerStaticWithOnlyAPathLeavesTheRestOfTheCheckToTheDashboard() {
    dashboard.answer(
        "POST",
        "/api/workstation/services/web/instances",
        201,
        """
        {"id": "7b1c", "host": "10.1.2.3", "port": 8080, "health": "critical",
         "check": {"type": "http", "path": "/health", "interval": "10s", "timeout": "2s"},
         "tags": [], "meta": {}, "checks": [], "outOfRange": false}
        """);

    Run run = yard("register", "static", "web", "--port", "8080", "--path", "/health");

    assertThat(run.exitCode()).isZero();
    dashboard.verifyCalled(
        "POST",
        "/api/workstation/services/web/instances",
        """
        {"port": 8080, "check": {"path": "/health"}}
        """);
  }

  @Test
  void registerStaticWithNoPublishAsksToLeaveANewServiceUnpublished() {
    dashboard.answer(
        "POST",
        "/api/workstation/services/web/instances",
        201,
        """
        {"id": "7b1c", "host": "10.1.2.3", "port": 8080, "health": "critical",
         "tags": [], "meta": {}, "checks": [], "outOfRange": false}
        """);

    Run run = yard("register", "static", "web", "--port", "8080", "--no-publish");

    assertThat(run.exitCode()).isZero();
    dashboard.verifyCalled(
        "POST",
        "/api/workstation/services/web/instances",
        """
        {"port": 8080, "publish": false}
        """);
  }

  @Test
  void registerStaticTakesNoContainerOption() {
    Run run = yard("register", "static", "web", "--port", "8080", "--container", "web-1");

    assertThat(run.exitCode()).isNotZero();
    assertThat(run.err()).contains("--container");
  }

  @Test
  void registerStaticWithAScriptCheckShowsWhyTheDashboardRefused() {
    dashboard.answer(
        "POST",
        "/api/workstation/services/web/instances",
        400,
        """
        {"status": 400, "detail": "Script checks are not offered: use an http or a tcp check."}
        """);

    Run run = yard("register", "static", "web", "--port", "8080", "--check", "script");

    assertThat(run.exitCode()).isNotZero();
    assertThat(run.err()).contains("Script checks are not offered");
  }

  @Test
  void containersListsTheUnfollowedContainersByComposeProjectWithTheirPorts() {
    dashboard.answer(
        "GET",
        "/api/workstation/containers",
        200,
        """
        [{"name": "storefront", "composeProject": "shop", "composeService": "web",
          "service": "web", "health": "healthy",
          "ports": [{"containerPort": 80, "hostPort": 8080}, {"containerPort": 9000}]},
         {"name": "grafana", "service": "grafana", "ports": [{"containerPort": 3000}]},
         {"name": "worker", "service": "worker", "ports": []}]
        """);

    Run run = yard("containers");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out())
        .isEqualTo(
            """
            Compose project shop
              storefront: ports 80 on host port 8080, 9000 not published; healthy. Registers as web.
            Other containers
              grafana: port 3000 not published. Registers as grafana.
              worker: no exposed port. Registers as worker.
            """);
  }

  @Test
  void containersSaysWhenEveryContainerIsFollowed() {
    dashboard.answer("GET", "/api/workstation/containers", 200, "[]");

    Run run = yard("containers");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out()).isEqualTo("Every running container is followed.\n");
  }

  @Test
  void profilesListsTheRouteProfilesWithTheirTypesAndTheServicesThatUseThem() {
    dashboard.answer(
        "GET",
        "/api/workstation/route-profiles",
        200,
        """
        [{"name": "behind-https", "type": "headers", "services": ["api", "web"]},
         {"name": "team-login", "type": "basicAuth", "services": []}]
        """);

    Run run = yard("profiles");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out())
        .isEqualTo(
            """
            behind-https: headers, used by api, web
            team-login: basicAuth, not used
            """);
  }

  @Test
  void profilesSaysWhenThereAreNone() {
    dashboard.answer("GET", "/api/workstation/route-profiles", 200, "[]");

    Run run = yard("profiles");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out()).isEqualTo("No Route Profiles.\n");
  }

  private static final String BACKUP =
      """
      version: 1
      exported-from:
        workstation: ws-a
        at: '2026-11-05T10:00:00Z'
      route-profiles: {}
      services:
        web:
          publication: none
          static-instances:
            - port: 8080
      aliases: {}
      """;

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

  private static final String PLAN =
      """
      {"exportedFrom": {"workstation": "ws-a", "at": "2026-11-05T10:00:00Z"},
       "mode": "add", "dryRun": %s,
       "entries": [
         {"kind": "route-profile", "name": "gzip", "action": "add"},
         {"kind": "service", "name": "web", "action": "skip", "reason": "It exists already."},
         {"kind": "static-instance", "name": "10.1.2.3:8080", "service": "web", "action": "add"},
         {"kind": "publication", "name": "web", "action": "skip",
          "reason": "It exists already."}]}
      """;

  private static final String PLAN_LINES =
      """
      Backup of ws-a at 2026-11-05T10:00:00Z.
      add Route Profile gzip
      skip Service web: It exists already.
      add Static Instance 10.1.2.3:8080 of web
      skip Publication of web: It exists already.
      """;

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
  void importReadsStandardInputForADash() throws Exception {
    dashboard.answer("POST", "/api/workstation/backup?mode=add", 200, PLAN.formatted(false));

    Run run = yardWithInput(BACKUP, "--dashboard", dashboard.url().toString(), "import", "-");

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
           {"kind": "alias", "name": "dash", "action": "add"}]}
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
            add Alias dash
            Added 1, updated 2, skipped 1.
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
           {"place": "services.web.static-instances[0].port", "reason": "The port is missing."},
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
            services.web.static-instances[0].port: The port is missing.
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
  void profilesShowPrintsTheProfileInTraefiksFileFormat() {
    dashboard.answer(
        "GET",
        "/api/workstation/route-profiles/team-login",
        200,
        """
        {"name": "team-login", "type": "basicAuth", "services": ["web"],
         "definition": {"basicAuth": {"users": ["ana:$2y$05$abc"], "removeHeader": true}}}
        """);

    Run run = yard("profiles", "show", "team-login");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out())
        .isEqualTo(
            """
            http:
              middlewares:
                team-login:
                  basicAuth:
                    users:
                      - ana:$2y$05$abc
                    removeHeader: true
            """);
  }

  @Test
  void profilesShowSaysWhenTheProfileDoesNotExist() {
    dashboard.answer(
        "GET",
        "/api/workstation/route-profiles/nope",
        404,
        """
        {"title": "Not Found", "status": 404,
         "detail": "This Workstation has no Route Profile nope."}
        """);

    Run run = yard("profiles", "show", "nope");

    assertThat(run.exitCode()).isNotZero();
    assertThat(run.err()).isEqualTo("This Workstation has no Route Profile nope.\n");
  }

  @Test
  void profilesSetSendsEveryProfileOfTheFileAsOneSet(@TempDir Path dir) throws Exception {
    dashboard.answer("PUT", "/api/workstation/route-profiles", 200, "[]");
    Path file = dir.resolve("profiles.yaml");
    Files.writeString(
        file,
        """
        http:
          middlewares:
            office-only:
              ipAllowList:
                sourceRange: ["192.168.1.0/24"]
            protected:
              chain:
                middlewares: [office-only]
        """);

    Run run = yard("profiles", "set", file.toString());

    assertThat(run.exitCode()).isZero();
    assertThat(run.out()).isEqualTo("Saved the Route Profiles office-only, protected.\n");
    dashboard.verifyCalled(
        "PUT",
        "/api/workstation/route-profiles",
        """
        {"office-only": {"ipAllowList": {"sourceRange": ["192.168.1.0/24"]}},
         "protected": {"chain": {"middlewares": ["office-only"]}}}
        """);
  }

  @Test
  void profilesSetTakesWhatProfilesShowPrints(@TempDir Path dir) throws Exception {
    String definition =
        """
        {"headers": {"customRequestHeaders": {"X-Forwarded-Proto": "https", "X-Number": "100",
                                              "X-Yes": "true", "X-On": "on", "X-Empty": "",
                                              "X-Colon": "a: b", "X-Hash": "#1", "X-Null": "null"},
                     "sslRedirect": true, "accessControlMaxAge": 100, "hostsProxyHeaders": []}}
        """;
    dashboard.answer(
        "GET",
        "/api/workstation/route-profiles/behind-https",
        200,
        "{\"name\": \"behind-https\", \"type\": \"headers\", \"services\": [], \"definition\": "
            + definition
            + "}");
    dashboard.answer("PUT", "/api/workstation/route-profiles", 200, "[]");
    Path file = dir.resolve("behind-https.yaml");
    Files.writeString(file, yard("profiles", "show", "behind-https").out());

    Run run = yard("profiles", "set", file.toString());

    assertThat(run.exitCode()).isZero();
    assertThat(run.out()).isEqualTo("Saved the Route Profile behind-https.\n");
    dashboard.verifyCalled(
        "PUT", "/api/workstation/route-profiles", "{\"behind-https\": " + definition + "}");
  }

  @Test
  void profilesSetReadsStandardInputForADash() throws Exception {
    dashboard.answer("PUT", "/api/workstation/route-profiles", 200, "[]");

    Run run =
        yardWithInput(
            """
            http:
              middlewares:
                gzip:
                  compress: {}
            """,
            "--dashboard",
            dashboard.url().toString(),
            "profiles",
            "set",
            "-");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out()).isEqualTo("Saved the Route Profile gzip.\n");
    dashboard.verifyCalled(
        "PUT", "/api/workstation/route-profiles", "{\"gzip\": {\"compress\": {}}}");
  }

  @Test
  void profilesSetPrintsEachReasonWhenTheDashboardRefusesTheSet(@TempDir Path dir)
      throws Exception {
    dashboard.answer(
        "PUT",
        "/api/workstation/route-profiles",
        400,
        """
        {"title": "Bad Request", "status": 400,
         "detail": "from-file reads its users from usersFile. typo has the type basicAut.",
         "problems": [{"profile": "from-file", "reason": "from-file reads its users from usersFile."},
                      {"profile": "typo", "reason": "typo has the type basicAut."}]}
        """);
    Path file = dir.resolve("profiles.yaml");
    Files.writeString(
        file,
        """
        http:
          middlewares:
            from-file:
              basicAuth:
                usersFile: /etc/users
            typo:
              basicAut: {}
        """);

    Run run = yard("profiles", "set", file.toString());

    assertThat(run.exitCode()).isNotZero();
    assertThat(run.out()).isEmpty();
    assertThat(run.err())
        .isEqualTo(
            """
            from-file reads its users from usersFile.
            typo has the type basicAut.
            """);
  }

  @Test
  void profilesSetRefusesAFileWithoutHttpMiddlewares(@TempDir Path dir) throws Exception {
    Path file = dir.resolve("profiles.yaml");
    Files.writeString(file, "gzip:\n  compress: {}\n");

    Run run = yard("profiles", "set", file.toString());

    assertThat(run.exitCode()).isNotZero();
    assertThat(run.err()).contains("http.middlewares");
    dashboard.verifyNotCalled();
  }

  @Test
  void profilesSetRefusesAFileThatIsNotYaml(@TempDir Path dir) throws Exception {
    Path file = dir.resolve("profiles.yaml");
    Files.writeString(file, "http:\n  middlewares: [unclosed\n");

    Run run = yard("profiles", "set", file.toString());

    assertThat(run.exitCode()).isNotZero();
    assertThat(run.err()).startsWith("The file is not valid YAML").contains("line");
  }

  @Test
  void profilesSetSaysWhenTheFileIsMissing(@TempDir Path dir) {
    Run run = yard("profiles", "set", dir.resolve("missing.yaml").toString());

    assertThat(run.exitCode()).isNotZero();
    assertThat(run.err()).contains("missing.yaml");
  }

  /** {@code GET} of {@code team} answers a headers profile that sets {@code X-Team: a}. */
  private void answerTeamProfile() {
    dashboard.answer(
        "GET",
        "/api/workstation/route-profiles/team",
        200,
        """
        {"name": "team", "type": "headers", "services": [],
         "definition": {"headers": {"customRequestHeaders": {"X-Team": "a"}}}}
        """);
  }

  private void answerTeamProfileSaved() {
    dashboard.answer(
        "PUT",
        "/api/workstation/route-profiles/team",
        200,
        """
        {"name": "team", "type": "headers", "services": [],
         "definition": {"headers": {"customRequestHeaders": {"X-Team": "b"}}}}
        """);
  }

  @Test
  void profilesEditSendsTheChangedProfile() throws Exception {
    answerTeamProfile();
    answerTeamProfileSaved();

    Run run =
        yardWithEditor("sed -i 's/X-Team: a/X-Team: b/' \"$1\"\n", "", "profiles", "edit", "team");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out()).isEqualTo("Saved the Route Profile team.\n");
    dashboard.verifyCalled(
        "PUT",
        "/api/workstation/route-profiles/team",
        "{\"headers\": {\"customRequestHeaders\": {\"X-Team\": \"b\"}}}");
  }

  @Test
  void profilesEditDoesNothingWhenTheTextIsTheSame() throws Exception {
    answerTeamProfile();

    Run run = yardWithEditor("true\n", "", "profiles", "edit", "team");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out()).isEqualTo("No change to the Route Profile team.\n");
    dashboard.verifyNotCalled("PUT", "/api/workstation/route-profiles/team");
  }

  @Test
  void profilesEditRefusesAnotherNameAndKeepsTheTextWhenAskedNotToEditAgain() throws Exception {
    answerTeamProfile();

    Run run = yardWithEditor("sed -i 's/team:/crew:/' \"$1\"\n", "n\n", "profiles", "edit", "team");

    assertThat(run.exitCode()).isNotZero();
    assertThat(run.err())
        .startsWith("The file must hold only the Route Profile team. It holds crew.\n")
        .contains("Edit again? [Y/n] ")
        .contains("Did not save the Route Profile team. Your text is in ");
    dashboard.verifyNotCalled("PUT", "/api/workstation/route-profiles/team");
  }

  @Test
  void profilesEditRefusesASecondProfile() throws Exception {
    answerTeamProfile();

    Run run =
        yardWithEditor(
            "printf '    gzip:\\n      compress: {}\\n' >> \"$1\"\n",
            "n\n",
            "profiles",
            "edit",
            "team");

    assertThat(run.exitCode()).isNotZero();
    assertThat(run.err())
        .startsWith("The file must hold only the Route Profile team. It holds team, gzip.\n");
    dashboard.verifyNotCalled("PUT", "/api/workstation/route-profiles/team");
  }

  @Test
  void profilesEditOffersTheSameTextAgainAfterARefusal() throws Exception {
    answerTeamProfile();
    answerTeamProfileSaved();

    // The first run renames the profile. The second finds that name, so the text is the same.
    Run run =
        yardWithEditor(
            """
            if grep -q crew: "$1"; then
              sed -i 's/crew:/team:/; s/X-Team: a/X-Team: b/' "$1"
            else
              sed -i 's/team:/crew:/' "$1"
            fi
            """,
            "\n",
            "profiles",
            "edit",
            "team");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out()).endsWith("Saved the Route Profile team.\n");
    dashboard.verifyCalled(
        "PUT",
        "/api/workstation/route-profiles/team",
        "{\"headers\": {\"customRequestHeaders\": {\"X-Team\": \"b\"}}}");
  }

  @Test
  void profilesEditPrintsEachReasonWhenTheDashboardRefusesTheProfile() throws Exception {
    answerTeamProfile();
    dashboard.answer(
        "PUT",
        "/api/workstation/route-profiles/team",
        400,
        """
        {"title": "Bad Request", "status": 400,
         "detail": "team has the type headerz.",
         "problems": [{"profile": "team", "reason": "team has the type headerz."}]}
        """);

    Run run =
        yardWithEditor("sed -i 's/headers:/headerz:/' \"$1\"\n", "n\n", "profiles", "edit", "team");

    assertThat(run.exitCode()).isNotZero();
    assertThat(run.err()).startsWith("team has the type headerz.\nEdit again? [Y/n] ");
  }

  @Test
  void profilesRemoveRemovesTheProfile() {
    dashboard.answer("DELETE", "/api/workstation/route-profiles/gzip", 204, null);

    Run run = yard("profiles", "remove", "gzip");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out()).isEqualTo("Removed the Route Profile gzip.\n");
    dashboard.verifyCalled("DELETE", "/api/workstation/route-profiles/gzip");
  }

  @Test
  void profilesRemovePrintsTheServicesAndChainsThatUseTheProfile() {
    dashboard.answer(
        "DELETE",
        "/api/workstation/route-profiles/login",
        409,
        """
        {"title": "Conflict", "status": 409,
         "detail": "login is in use, so it cannot be removed. The Publications of these Services name it: web. These chains name it: protected.",
         "services": ["web"], "chains": ["protected"]}
        """);

    Run run = yard("profiles", "remove", "login");

    assertThat(run.exitCode()).isNotZero();
    assertThat(run.err())
        .isEqualTo(
            "login is in use, so it cannot be removed. The Publications of these Services name"
                + " it: web. These chains name it: protected.\n");
  }

  @Test
  void registerContainerRegistersTheContainerAndSaysWhereItIsPublished() {
    dashboard.answer(
        "POST",
        "/api/workstation/containers/grafana/register",
        200,
        """
        {"workstation": "ws-a", "name": "grafana", "state": "healthy",
         "publication": {"scope": "local"}, "hostname": "grafana.lvh.me",
         "containerBindings": [], "warnings": [],
         "instances": [{"id": "grafana:grafana:3000", "host": "172.17.0.2", "port": 3000,
                        "health": "passing", "outOfRange": false,
                        "checks": [], "tags": [], "meta": {}}]}
        """);

    Run run = yard("register", "container", "grafana");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out())
        .isEqualTo("Registered grafana as grafana, published at grafana.lvh.me.\n");
    dashboard.verifyCalled("POST", "/api/workstation/containers/grafana/register", "{}");
  }

  @Test
  void registerContainerSendsThePortTheServiceAndNoPublish() {
    dashboard.answer(
        "POST",
        "/api/workstation/containers/storefront/register",
        200,
        """
        {"workstation": "ws-a", "name": "shop", "state": "healthy",
         "containerBindings": [], "warnings": [],
         "instances": [{"id": "shop:storefront:9000", "host": "172.17.0.2", "port": 9000,
                        "health": "passing", "outOfRange": false,
                        "checks": [], "tags": [], "meta": {}}]}
        """);

    Run run =
        yard(
            "register",
            "container",
            "storefront",
            "--port",
            "9000",
            "--service",
            "shop",
            "--no-publish");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out()).isEqualTo("Registered storefront as shop. shop is not published.\n");
    dashboard.verifyCalled(
        "POST",
        "/api/workstation/containers/storefront/register",
        """
        {"port": 9000, "service": "shop", "publish": false}
        """);
  }

  @Test
  void registerContainerOfAContainerThatDoesNotRunSaysWhenItGetsItsInstance() {
    dashboard.answer(
        "POST",
        "/api/workstation/containers/catalog/register",
        200,
        """
        {"workstation": "ws-a", "name": "catalog", "state": "unavailable",
         "publication": {"scope": "local"}, "hostname": "catalog.lvh.me",
         "containerBindings": [], "warnings": [], "instances": []}
        """);

    Run run = yard("register", "container", "catalog", "--port", "80");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out())
        .isEqualTo(
            "Registered catalog as catalog, published at catalog.lvh.me. No container"
                + " of that name runs now: catalog gets its Bound Instance once one starts.\n");
    dashboard.verifyCalled(
        "POST", "/api/workstation/containers/catalog/register", "{\"port\": 80}");
  }

  @Test
  void registerContainerOfAContainerThatDoesNotRunWithoutAPortSaysToGiveIt() {
    dashboard.answer(
        "POST",
        "/api/workstation/containers/catalog/register",
        400,
        """
        {"status": 400, "field": "port",
         "detail": "The container catalog does not run: give the port."}
        """);

    Run run = yard("register", "container", "catalog");

    assertThat(run.exitCode()).isEqualTo(1);
    assertThat(run.err()).contains("The container catalog does not run: give the port.");
  }

  @Test
  void registerContainerWithoutAPortOnAContainerWithSeveralListsThePorts() {
    dashboard.answer(
        "POST",
        "/api/workstation/containers/storefront/register",
        400,
        """
        {"status": 400, "field": "port",
         "detail": "The container storefront has more than one port: give one of 80, 9000."}
        """);

    Run run = yard("register", "container", "storefront");

    assertThat(run.exitCode()).isEqualTo(1);
    assertThat(run.err()).contains("give one of 80, 9000.");
  }

  @Test
  void registerContainerOfAFollowedContainerSaysWhichServiceFollowsIt() {
    dashboard.answer(
        "POST",
        "/api/workstation/containers/grafana/register",
        409,
        """
        {"status": 409,
         "detail": "The container grafana is already followed by a Container Binding of the Service metrics."}
        """);

    Run run = yard("register", "container", "grafana");

    assertThat(run.exitCode()).isEqualTo(1);
    assertThat(run.err())
        .contains("already followed by a Container Binding of the Service metrics.");
  }

  @Test
  void registerContainerPrintsTheServicesWarnings() {
    dashboard.answer(
        "POST",
        "/api/workstation/containers/web-1/register",
        200,
        """
        {"workstation": "ws-a", "name": "web", "state": "healthy",
         "publication": {"scope": "yard"}, "hostname": "web.lvh.me",
         "containerBindings": [], "warnings": [
            {"kind": "unpublished-container-port",
             "message": "The Bound Instance at 172.17.0.2:80 is on a container port that is not published."}],
         "instances": [{"id": "web:web-1:80", "host": "172.17.0.2", "port": 80,
                        "health": "passing", "outOfRange": false,
                        "checks": [], "tags": [], "meta": {}}]}
        """);

    Run run = yard("register", "container", "web-1", "--service", "web");

    assertThat(run.exitCode()).isZero();
    assertThat(run.err())
        .isEqualTo(
            "Warning: The Bound Instance at 172.17.0.2:80 is on a container port that is not"
                + " published.\n");
  }

  @Test
  void registerContainerWithComposeBindsTheComposeServiceIntoAServiceOfItsCleanedName() {
    dashboard.answer(
        "POST",
        "/api/workstation/services/my-web/container-bindings",
        201,
        """
        {"id": "compose:shop:my_web:80", "composeProject": "shop", "composeService": "my_web",
         "port": 80, "instances": ["my-web:storefront:80", "my-web:shop-web-2:80"]}
        """);

    Run run = yard("register", "container", "--compose", "shop/my_web", "--port", "80");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out())
        .isEqualTo(
            "Bound my-web to Compose service my_web of project shop, port 80. 2 of its"
                + " containers run now, so my-web has 2 Bound Instances.\n");
    dashboard.verifyCalled(
        "POST",
        "/api/workstation/services/my-web/container-bindings",
        """
        {"composeProject": "shop", "composeService": "my_web", "port": 80}
        """);
  }

  @Test
  void registerContainerWithComposeSendsTheServiceAndNoPublish() {
    dashboard.answer(
        "POST",
        "/api/workstation/services/shop/container-bindings",
        201,
        """
        {"id": "compose:shop:web:80", "composeProject": "shop", "composeService": "web", "port": 80,
         "instances": []}
        """);

    Run run =
        yard(
            "register",
            "container",
            "--compose",
            "shop/web",
            "--port",
            "80",
            "--service",
            "shop",
            "--no-publish");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out())
        .isEqualTo(
            "Bound shop to Compose service web of project shop, port 80. None of its"
                + " containers runs now: shop gets a Bound Instance for each one that starts.\n");
    dashboard.verifyCalled(
        "POST",
        "/api/workstation/services/shop/container-bindings",
        """
        {"composeProject": "shop", "composeService": "web", "port": 80, "publish": false}
        """);
  }

  @Test
  void registerContainerWithComposeNeedsAPort() {
    Run run = yard("register", "container", "--compose", "shop/web");

    assertThat(run.exitCode()).isNotZero();
    assertThat(run.err()).isEqualTo("A Compose service needs --port.\n");
  }

  @Test
  void registerContainerWithComposeNeedsAProjectAndAService() {
    Run run = yard("register", "container", "--compose", "web", "--port", "80");

    assertThat(run.exitCode()).isNotZero();
    assertThat(run.err())
        .isEqualTo("Give --compose as <project>/<service>, such as shop/web, not web.\n");
  }

  @Test
  void registerContainerWithAContainerAndComposeIsRefused() {
    Run run = yard("register", "container", "web-1", "--compose", "shop/web", "--port", "80");

    assertThat(run.exitCode()).isNotZero();
    assertThat(run.err()).isEqualTo("Give a container or --compose, not both.\n");
  }

  @Test
  void registerContainerNeedsAContainerOrCompose() {
    Run run = yard("register", "container");

    assertThat(run.exitCode()).isNotZero();
    assertThat(run.err()).isEqualTo("Give a container, or --compose <project>/<service>.\n");
  }

  @Test
  void registerContainerWithComposeThatABindingFollowsSaysWhichServiceFollowsIt() {
    dashboard.answer(
        "POST",
        "/api/workstation/services/web/container-bindings",
        409,
        """
        {"status": 409,
         "detail": "The Compose service web of project shop is already followed by a Container Binding of the Service shop."}
        """);

    Run run = yard("register", "container", "--compose", "shop/web", "--port", "80");

    assertThat(run.exitCode()).isEqualTo(1);
    assertThat(run.err()).contains("of the Service shop.");
  }

  @Test
  void registerAloneShowsItsTwoSubcommands() {
    Run run = yard("register");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out()).contains("static").contains("container");
  }

  @Test
  void registerWithAServiceAndNoSubcommandIsRefused() {
    Run run = yard("register", "web", "--port", "80");

    assertThat(run.exitCode()).isEqualTo(2);
    assertThat(run.err()).contains("web").contains("static").contains("container");
  }

  @Test
  void removeWithContainerRemovesThatContainerBinding() {
    answerWebWithBindings(
        """
        {"id": "web-1:80", "container": "web-1", "port": 80, "instances": []}\
        """);
    dashboard.answer(
        "DELETE", "/api/workstation/services/web/container-bindings/web-1%3A80", 204, null);

    Run run = yard("remove", "web", "--container", "web-1");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out())
        .isEqualTo("Removed the Container Binding of web to container web-1, port 80.\n");
    dashboard.verifyCalled("DELETE", "/api/workstation/services/web/container-bindings/web-1%3A80");
  }

  @Test
  void removeWithContainerAndPortPicksThatPort() {
    answerWebWithBindings(
        """
        {"id": "web-1:80", "container": "web-1", "port": 80, "instances": []},
        {"id": "web-1:443", "container": "web-1", "port": 443, "instances": []}\
        """);
    dashboard.answer(
        "DELETE", "/api/workstation/services/web/container-bindings/web-1%3A443", 204, null);

    Run run = yard("remove", "web", "--container", "web-1", "--port", "443");

    assertThat(run.exitCode()).isZero();
    dashboard.verifyCalled(
        "DELETE", "/api/workstation/services/web/container-bindings/web-1%3A443");
  }

  @Test
  void removeWithContainerBoundOnMoreThanOnePortAsksForThePort() {
    answerWebWithBindings(
        """
        {"id": "web-1:80", "container": "web-1", "port": 80, "instances": []},
        {"id": "web-1:443", "container": "web-1", "port": 443, "instances": []}\
        """);

    Run run = yard("remove", "web", "--container", "web-1");

    assertThat(run.exitCode()).isNotZero();
    assertThat(run.err())
        .isEqualTo(
            "web is bound to container web-1 on more than one port (80, 443): give" + " --port.\n");
  }

  @Test
  void removeWithContainerThatIsNotBoundSaysSo() {
    answerWebWithBindings("");

    Run run = yard("remove", "web", "--container", "web-1");

    assertThat(run.exitCode()).isNotZero();
    assertThat(run.err()).isEqualTo("web has no Container Binding to container web-1.\n");
  }

  @Test
  void removeWithComposeServiceRemovesThatContainerBinding() {
    answerWebWithBindings(
        """
        {"id": "web-1:80", "container": "web-1", "port": 80, "instances": []},
        {"id": "compose:shop:web:80", "composeProject": "shop", "composeService": "web", "port": 80,
         "instances": []}\
        """);
    dashboard.answer(
        "DELETE",
        "/api/workstation/services/web/container-bindings/compose%3Ashop%3Aweb%3A80",
        204,
        null);

    Run run = yard("remove", "web", "--compose-project", "shop", "--compose-service", "web");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out())
        .isEqualTo(
            "Removed the Container Binding of web to Compose service web of project"
                + " shop, port 80.\n");
    dashboard.verifyCalled(
        "DELETE", "/api/workstation/services/web/container-bindings/compose%3Ashop%3Aweb%3A80");
  }

  @Test
  void removeWithComposeServiceThatIsNotBoundSaysSo() {
    answerWebWithBindings(
        """
        {"id": "web-1:80", "container": "web-1", "port": 80, "instances": []}\
        """);

    Run run = yard("remove", "web", "--compose-project", "shop", "--compose-service", "web");

    assertThat(run.exitCode()).isNotZero();
    assertThat(run.err())
        .isEqualTo("web has no Container Binding to Compose service web of project shop.\n");
  }

  @Test
  void removeByIdRemovesThatInstance() {
    dashboard.answer("DELETE", "/api/workstation/services/web/instances/7b1c", 204, null);

    Run run = yard("remove", "web", "--id", "7b1c");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out()).isEqualTo("Removed Instance 7b1c of web.\n");
    dashboard.verifyCalled("DELETE", "/api/workstation/services/web/instances/7b1c");
  }

  @Test
  void removeByPortRemovesTheInstanceAtThatPort() {
    answerWebWithInstances(
        """
        {"id": "7b1c", "host": "10.1.2.3", "port": 8080, "health": "passing",
         "tags": [], "meta": {}, "checks": [], "outOfRange": false},
        {"id": "9d4e", "host": "10.1.2.3", "port": 8081, "health": "passing",
         "tags": [], "meta": {}, "checks": [], "outOfRange": false}\
        """);
    dashboard.answer("DELETE", "/api/workstation/services/web/instances/9d4e", 204, null);

    Run run = yard("remove", "web", "--port", "8081");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out()).isEqualTo("Removed web at 10.1.2.3:8081.\n");
    dashboard.verifyCalled("DELETE", "/api/workstation/services/web/instances/9d4e");
  }

  @Test
  void removeByHostAndPortPicksTheInstanceAtThatHost() {
    answerWebWithInstances(
        """
        {"id": "7b1c", "host": "10.1.2.3", "port": 8080, "health": "passing",
         "tags": [], "meta": {}, "checks": [], "outOfRange": false},
        {"id": "9d4e", "host": "10.1.2.4", "port": 8080, "health": "passing",
         "tags": [], "meta": {}, "checks": [], "outOfRange": false}\
        """);
    dashboard.answer("DELETE", "/api/workstation/services/web/instances/9d4e", 204, null);

    Run run = yard("remove", "web", "--host", "10.1.2.4", "--port", "8080");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out()).isEqualTo("Removed web at 10.1.2.4:8080.\n");
  }

  @Test
  void removeByPortOnMoreThanOneHostAsksForTheHost() {
    answerWebWithInstances(
        """
        {"id": "7b1c", "host": "10.1.2.3", "port": 8080, "health": "passing",
         "tags": [], "meta": {}, "checks": [], "outOfRange": false},
        {"id": "9d4e", "host": "10.1.2.4", "port": 8080, "health": "passing",
         "tags": [], "meta": {}, "checks": [], "outOfRange": false}\
        """);

    Run run = yard("remove", "web", "--port", "8080");

    assertThat(run.exitCode()).isNotZero();
    assertThat(run.err()).contains("10.1.2.3").contains("10.1.2.4").contains("--host");
  }

  @Test
  void removeByPortWithNoInstanceThereSaysSo() {
    answerWebWithInstances(
        """
        {"id": "7b1c", "host": "10.1.2.3", "port": 8080, "health": "passing",
         "tags": [], "meta": {}, "checks": [], "outOfRange": false}\
        """);

    Run run = yard("remove", "web", "--port", "9090");

    assertThat(run.exitCode()).isNotZero();
    assertThat(run.err()).isEqualTo("web has no Instance at port 9090.\n");
  }

  @Test
  void removeNeedsAnIdAPortOrAContainer() {
    Run run = yard("remove", "web");

    assertThat(run.exitCode()).isEqualTo(CommandLine.ExitCode.USAGE);
    assertThat(run.err()).contains("--id").contains("--port").contains("--container");
  }

  @Test
  void publishPublishesWithLocalScopeAndShowsTheHostname() {
    dashboard.answer(
        "PUT",
        "/api/workstation/services/web/publication",
        200,
        """
        {"workstation": "ws-a", "name": "web", "state": "unhealthy", "publication": {"scope": "local"},
         "hostname": "web.lvh.me", "containerBindings": [], "warnings": [], "instances": [
            {"id": "7b1c", "host": "10.1.2.3", "port": 8080, "health": "critical",
             "tags": [], "meta": {}, "checks": [], "outOfRange": false}]}
        """);

    Run run = yard("publish", "web");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out())
        .isEqualTo("Published web at web.lvh.me, with Local Scope. It is unhealthy.\n");
    dashboard.verifyCalled("PUT", "/api/workstation/services/web/publication");
  }

  @Test
  void publishWithYardScopeSendsTheScope() {
    dashboard.answer(
        "PUT",
        "/api/workstation/services/web/publication",
        200,
        """
        {"workstation": "ws-a", "name": "web", "state": "healthy", "publication": {"scope": "yard"},
         "hostname": "web.lvh.me", "containerBindings": [],
          "warnings": [], "instances": [
            {"id": "7b1c", "host": "10.1.2.3", "port": 8080, "health": "passing",
             "tags": [], "meta": {}, "checks": [], "outOfRange": false}]}
        """);

    Run run = yard("publish", "web", "--scope", "yard");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out())
        .isEqualTo(
            "Published web at web.lvh.me, with Yard Scope. Other Workstations reach it at"
                + " web.ws-a.<base-domain>. It is healthy.\n");
    dashboard.verifyCalled(
        "PUT",
        "/api/workstation/services/web/publication",
        """
        {"scope": "yard"}
        """);
  }

  @Test
  void publishWithPrioritySendsTheOrder() {
    dashboard.answer(
        "PUT",
        "/api/workstation/services/web/publication",
        200,
        """
        {"workstation": "ws-a", "name": "web", "state": "healthy", "publication": {"scope": "local"},
         "hostname": "web.lvh.me", "containerBindings": [], "warnings": [],
         "priority": ["bound", "static"], "instances": []}
        """);

    Run run = yard("publish", "web", "--priority", "bound,static");

    assertThat(run.exitCode()).isZero();
    dashboard.verifyCalled(
        "PUT",
        "/api/workstation/services/web/publication",
        """
        {"priority": ["bound", "static"]}
        """);
  }

  @Test
  void publishWithPriorityOffSendsAnEmptyOrder() {
    dashboard.answer(
        "PUT",
        "/api/workstation/services/web/publication",
        200,
        """
        {"workstation": "ws-a", "name": "web", "state": "healthy", "publication": {"scope": "local"},
         "hostname": "web.lvh.me", "containerBindings": [], "warnings": [],
         "priority": [], "instances": []}
        """);

    Run run = yard("publish", "web", "--priority", "off");

    assertThat(run.exitCode()).isZero();
    dashboard.verifyCalled(
        "PUT",
        "/api/workstation/services/web/publication",
        """
        {"priority": []}
        """);
    assertThat(yard("publish", "--help").out()).contains("off:");
  }

  @Test
  void publishWithProfilesSendsThemInOrderAndPrintsTheAuthWarning() {
    dashboard.answer(
        "PUT",
        "/api/workstation/services/admin/publication",
        200,
        """
        {"workstation": "ws-a", "name": "admin", "state": "healthy",
         "publication": {"scope": "local"}, "hostname": "admin.lvh.me", "containerBindings": [],
         "profiles": ["basic-auth", "cors"],
         "warnings": [
            {"kind": "route-profile-auth",
             "message": "The Route Profile basic-auth asks who the user is only on the hostname."}],
         "instances": []}
        """);

    Run run = yard("publish", "admin", "--profile", "basic-auth", "--profile", "cors");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out()).contains("Route Profiles: basic-auth, cors.");
    assertThat(run.err())
        .isEqualTo(
            "Warning: The Route Profile basic-auth asks who the user is only on the hostname.\n");
    dashboard.verifyCalled(
        "PUT",
        "/api/workstation/services/admin/publication",
        """
        {"profiles": ["basic-auth", "cors"]}
        """);
  }

  @Test
  void publishWithNoProfilesSendsAnEmptyList() {
    dashboard.answer(
        "PUT",
        "/api/workstation/services/admin/publication",
        200,
        """
        {"workstation": "ws-a", "name": "admin", "state": "healthy",
         "publication": {"scope": "local"}, "hostname": "admin.lvh.me", "containerBindings": [],
         "warnings": [], "instances": []}
        """);

    Run run = yard("publish", "admin", "--no-profiles");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out()).doesNotContain("Route Profiles");
    dashboard.verifyCalled(
        "PUT",
        "/api/workstation/services/admin/publication",
        """
        {"profiles": []}
        """);
  }

  @Test
  void publishRefusesProfilesWithNoProfiles() {
    Run run = yard("publish", "admin", "--profile", "cors", "--no-profiles");

    assertThat(run.exitCode()).isEqualTo(CommandLine.ExitCode.USAGE);
    assertThat(run.err()).contains("--profile").contains("--no-profiles");
  }

  @Test
  void publishHelpSendsToTheProfilesCommandForTheNames() {
    assertThat(yard("publish", "--help").out())
        .contains("lvh-yard profiles")
        .doesNotContain("route-profiles.yaml");
  }

  @Test
  void publishWithARefusedPriorityShowsWhyAndExitsWithOne() {
    dashboard.answer(
        "PUT",
        "/api/workstation/services/web/publication",
        400,
        """
        {"status": 400,
         "detail": "The Instance Priority has the kind bound more than once."}
        """);

    Run run = yard("publish", "web", "--priority", "bound,static,bound");

    assertThat(run.exitCode()).isEqualTo(1);
    assertThat(run.err()).contains("The Instance Priority has the kind bound more than once.");
  }

  @Test
  void publishPrintsTheServicesWarnings() {
    dashboard.answer(
        "PUT",
        "/api/workstation/services/web/publication",
        200,
        """
        {"workstation": "ws-a", "name": "web", "state": "healthy", "publication": {"scope": "yard"},
         "hostname": "web.lvh.me", "containerBindings": [], "warnings": [
            {"kind": "loopback-instance",
             "message": "The Instance at 127.0.0.1:8080 is on a loopback address."}],
         "instances": [
            {"id": "7b1c", "host": "127.0.0.1", "port": 8080, "health": "passing",
             "tags": [], "meta": {}, "checks": [], "outOfRange": false}]}
        """);

    Run run = yard("publish", "web", "--scope", "yard");

    assertThat(run.exitCode()).isZero();
    assertThat(run.err())
        .isEqualTo("Warning: The Instance at 127.0.0.1:8080 is on a loopback address.\n");
  }

  @Test
  void publishRefusesAnUnknownScope() {
    Run run = yard("publish", "web", "--scope", "world");

    assertThat(run.exitCode()).isEqualTo(CommandLine.ExitCode.USAGE);
    assertThat(run.err()).contains("local").contains("yard");
  }

  @Test
  void unpublishRemovesThePublication() {
    dashboard.answer("DELETE", "/api/workstation/services/web/publication", 204, null);

    Run run = yard("unpublish", "web");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out()).isEqualTo("Unpublished web. Its Instances stay registered.\n");
    dashboard.verifyCalled("DELETE", "/api/workstation/services/web/publication");
  }

  @Test
  void publishingUnknownServiceIsRefused() {
    dashboard.answer(
        "PUT",
        "/api/workstation/services/wbe/publication",
        404,
        """
        {"type": "about:blank", "title": "Not Found", "status": 404,
         "detail": "This Workstation has no Service wbe."}
        """);

    Run run = yard("publish", "wbe");

    assertThat(run.exitCode()).isNotZero();
    assertThat(run.err()).isEqualTo("This Workstation has no Service wbe.\n");
  }

  @Test
  void statusPrintsEachPartWithItsReasonAndDetailsAndSucceedsWhenAllAreOk() {
    dashboard.answer(
        "GET",
        "/api/status",
        200,
        """
        {"overall": "ok", "parts": [
          {"name": "Consul", "state": "ok", "reason": "Consul has a leader, and 2 Workstations are in its WAN pool.",
           "details": [{"name": "leader", "value": "10.0.0.1:8300"},
                       {"name": "ws-b", "value": "alive at 10.0.0.2:8302", "state": "ok"}]},
          {"name": "Docker", "state": "ok", "reason": "Docker answers, so Container Bindings work.",
           "details": [{"name": "version", "value": "28.1.1"}]}]}
        """);

    Run run = yard("status");

    assertThat(run.exitCode()).isZero();
    assertThat(run.lines())
        .containsExactly(
            "LVH Yard on this Workstation: ok",
            "  ok    Consul: Consul has a leader, and 2 Workstations are in its WAN pool.",
            "          leader: 10.0.0.1:8300",
            "          ws-b: alive at 10.0.0.2:8302",
            "  ok    Docker: Docker answers, so Container Bindings work.",
            "          version: 28.1.1");
  }

  @Test
  void statusShowsTraefikWithItsDetailsAndSucceedsOnAWarning() {
    dashboard.answer(
        "GET",
        "/api/status",
        200,
        """
        {"overall": "warning", "parts": [
          {"name": "Consul", "state": "ok", "reason": "Consul has a leader.", "details": []},
          {"name": "Traefik", "state": "warning",
           "reason": "Traefik answers, but last fetched its Routes from this dashboard 40 s ago, more than 15 s.",
           "details": [{"name": "ping", "value": "OK at http://127.0.0.1:8080/ping", "state": "ok"},
                       {"name": "last fetch", "value": "40 s ago", "state": "warning"}]},
          {"name": "Docker", "state": "ok", "reason": "Docker answers, so Container Bindings work.", "details": []}]}
        """);

    Run run = yard("status");

    assertThat(run.exitCode()).isZero();
    assertThat(run.lines())
        .containsExactly(
            "LVH Yard on this Workstation: warning",
            "  ok    Consul: Consul has a leader.",
            "  WARN  Traefik: Traefik answers, but last fetched its Routes from this dashboard 40 s"
                + " ago, more than 15 s.",
            "          ping: OK at http://127.0.0.1:8080/ping",
            "          last fetch: 40 s ago (warning)",
            "  ok    Docker: Docker answers, so Container Bindings work.");
  }

  @Test
  void statusHelpSaysThatItShowsWhetherLvhYardWorks() {
    assertThat(yard("status", "--help").out())
        .contains("Shows whether LVH Yard works on this Workstation.")
        .doesNotContain("Core Services");
  }

  @Test
  void statusFailsWhenAnyPartIsFailing() {
    dashboard.answer(
        "GET",
        "/api/status",
        200,
        """
        {"overall": "failing", "parts": [
          {"name": "Consul", "state": "ok", "reason": "Consul has a leader.", "details": []},
          {"name": "Docker", "state": "failing",
           "reason": "No Docker socket at /var/run/docker.sock. Start Docker, or fix DOCKER_HOST.", "details": []}]}
        """);

    Run run = yard("status");

    assertThat(run.exitCode()).isEqualTo(1);
    assertThat(run.lines())
        .containsExactly(
            "LVH Yard on this Workstation: failing",
            "  ok    Consul: Consul has a leader.",
            "  FAIL  Docker: No Docker socket at /var/run/docker.sock. Start Docker, or fix"
                + " DOCKER_HOST.");
  }

  @Test
  void statusWithWarningsOnlySucceedsUnlessStrict() {
    dashboard.answer(
        "GET",
        "/api/status",
        200,
        """
        {"overall": "warning", "parts": [
          {"name": "Consul", "state": "warning", "reason": "A Peer is not in Consul's WAN pool: 10.0.0.9.",
           "details": [{"name": "peer 10.0.0.9", "value": "not in the WAN pool", "state": "warning"}]}]}
        """);

    Run lenient = yard("status");
    Run strict = yard("status", "--strict");

    assertThat(lenient.exitCode()).isZero();
    assertThat(lenient.lines())
        .containsExactly(
            "LVH Yard on this Workstation: warning",
            "  WARN  Consul: A Peer is not in Consul's WAN pool: 10.0.0.9.",
            "          peer 10.0.0.9: not in the WAN pool (warning)");
    assertThat(strict.exitCode()).isEqualTo(1);
  }

  @Test
  void statusSaysWhenTheDashboardDoesNotAnswer() throws IOException {
    String nothingListens = "http://127.0.0.1:" + freePort();

    Run run = yardAsTyped("--dashboard", nothingListens, "status");

    assertThat(run.exitCode()).isEqualTo(1);
    assertThat(run.err()).contains("The dashboard does not answer at").contains(nothingListens);
  }

  @Test
  void testPrintsEachCheckOfAnOwnServiceAndSucceedsWhenAllPass() {
    dashboard.answer(
        "GET",
        "/api/workstation/services/web/reachability",
        200,
        """
        {"workstation": "ws-a", "service": "web", "passed": true,
         "instances": [{"id": "7b1c", "address": "10.1.2.3:8080", "reached": true}],
         "routes": [{"hostname": "web.lvh.me", "reached": true, "status": 200}]}
        """);

    Run run = yard("test", "web");

    assertThat(run.exitCode()).isZero();
    assertThat(run.lines())
        .containsExactly(
            "Reachability of web on ws-a, from this Workstation:",
            "  ok    Instance 10.1.2.3:8080",
            "  ok    Route web.lvh.me: answered 200",
            "Every check passed.");
  }

  @Test
  void testFailsWhenAnyCheckFailsAndSaysWhy() {
    dashboard.answer(
        "GET",
        "/api/workstation/services/web/reachability",
        200,
        """
        {"workstation": "ws-a", "service": "web", "passed": false,
         "instances": [{"id": "7b1c", "address": "10.1.2.3:8080", "reached": true},
                       {"id": "8c2d", "address": "10.1.2.3:8081", "reached": false,
                        "error": "Connection refused"}],
         "routes": [{"hostname": "web.lvh.me", "reached": false, "status": 502,
                     "error": "Traefik answered 502: no Instance answered."}]}
        """);

    Run run = yard("test", "web");

    assertThat(run.exitCode()).isEqualTo(1);
    assertThat(run.lines())
        .containsExactly(
            "Reachability of web on ws-a, from this Workstation:",
            "  ok    Instance 10.1.2.3:8080",
            "  FAIL  Instance 10.1.2.3:8081: Connection refused",
            "  FAIL  Route web.lvh.me: Traefik answered 502: no Instance answered.",
            "2 of 3 checks failed.");
  }

  @Test
  void testOfARemoteServiceWithoutARouteSaysSo() {
    dashboard.answer(
        "GET",
        "/api/services/ws-b/grafana/reachability",
        200,
        """
        {"workstation": "ws-b", "service": "grafana", "passed": true,
         "instances": [{"id": "1a2b", "address": "192.168.1.20:3000", "reached": true}], "routes": [],
         "noRoute": "Published with Local Scope: only ws-b routes it, so only its Instances are tested from here."}
        """);

    Run run = yard("test", "grafana", "--workstation", "ws-b");

    assertThat(run.exitCode()).isZero();
    assertThat(run.lines())
        .containsExactly(
            "Reachability of grafana on ws-b, from this Workstation:",
            "  ok    Instance 192.168.1.20:3000",
            "Published with Local Scope: only ws-b routes it, so only its Instances are tested from"
                + " here.",
            "Every check passed.");
  }

  @Test
  void testSkipsAnInstanceOutOfRangeWithoutFailing() {
    dashboard.answer(
        "GET",
        "/api/services/ws-b/grafana/reachability",
        200,
        """
        {"workstation": "ws-b", "service": "grafana", "passed": true,
         "instances": [{"id": "1a2b", "address": "192.168.1.20:3000", "reached": true},
                       {"id": "3c4d", "address": "127.0.0.1:3000", "reached": false, "outOfRange": true}],
         "routes": [{"hostname": "grafana.ws-b.lvh.me", "reached": true, "status": 200}]}
        """);

    Run run = yard("test", "grafana", "--workstation", "ws-b");

    assertThat(run.exitCode()).isZero();
    assertThat(run.lines())
        .containsExactly(
            "Reachability of grafana on ws-b, from this Workstation:",
            "  ok    Instance 192.168.1.20:3000",
            "  skip  Instance 127.0.0.1:3000: out of range",
            "  ok    Route grafana.ws-b.lvh.me: answered 200",
            "Every check passed. 1 skipped.");
  }

  @Test
  void testMarksTheInstancesTheRouteUsesNow() {
    dashboard.answer(
        "GET",
        "/api/workstation/services/web/reachability",
        200,
        """
        {"workstation": "ws-a", "service": "web", "passed": true,
         "instances": [{"id": "7b1c", "address": "10.1.2.3:8080", "reached": true, "routed": true},
                       {"id": "8c2d", "address": "172.18.0.5:80", "reached": true}],
         "routes": [{"hostname": "web.lvh.me", "reached": true, "status": 200}],
         "routing": {"kind": "static", "instances": ["7b1c"]}}
        """);

    Run run = yard("test", "web");

    assertThat(run.exitCode()).isZero();
    assertThat(run.lines())
        .containsExactly(
            "Reachability of web on ws-a, from this Workstation:",
            "  ok    Instance 10.1.2.3:8080 (in the Route)",
            "  ok    Instance 172.18.0.5:80 (not in the Route now)",
            "  ok    Route web.lvh.me: answered 200",
            "The Route uses the passing Static Instances now.",
            "Every check passed.");
  }

  @Test
  void testSaysWhenTheInstancePriorityIsOff() {
    dashboard.answer(
        "GET",
        "/api/workstation/services/web/reachability",
        200,
        """
        {"workstation": "ws-a", "service": "web", "passed": true,
         "instances": [{"id": "7b1c", "address": "10.1.2.3:8080", "reached": true, "routed": true},
                       {"id": "8c2d", "address": "172.18.0.5:80", "reached": true, "routed": true}],
         "routes": [{"hostname": "web.lvh.me", "reached": true, "status": 200}],
         "routing": {"off": true, "instances": ["7b1c", "8c2d"]}}
        """);

    Run run = yard("test", "web");

    assertThat(run.exitCode()).isZero();
    assertThat(run.lines())
        .containsExactly(
            "Reachability of web on ws-a, from this Workstation:",
            "  ok    Instance 10.1.2.3:8080 (in the Route)",
            "  ok    Instance 172.18.0.5:80 (in the Route)",
            "  ok    Route web.lvh.me: answered 200",
            "The Instance Priority is off, so the Route uses every passing Instance,"
                + " or every Instance in range if none passes.",
            "Every check passed.");
  }

  @Test
  void testSaysWhenTheRouteUsesTheAlias() {
    dashboard.answer(
        "GET",
        "/api/workstation/services/web/reachability",
        200,
        """
        {"workstation": "ws-a", "service": "web", "passed": false,
         "instances": [{"id": "7b1c", "address": "10.1.2.3:8080", "reached": false,
                        "error": "Connection refused"}],
         "routes": [{"hostname": "web.lvh.me", "reached": true, "status": 200}],
         "routing": {"kind": "alias", "instances": ["9e3f"]},
         "alias": {"name": "web", "workstation": "ws-b", "service": "web",
                   "hostname": "web.lvh.me", "state": "fallback"}}
        """);

    Run run = yard("test", "web");

    assertThat(run.exitCode()).isEqualTo(1);
    assertThat(run.lines())
        .containsExactly(
            "Reachability of web on ws-a, from this Workstation:",
            "  FAIL  Instance 10.1.2.3:8080 (not in the Route now): Connection refused",
            "  ok    Route web.lvh.me: answered 200",
            "The Route sends requests to the Alias's Remote Service: web on ws-b.",
            "1 of 2 checks failed.");
  }

  @Test
  void testOfUnknownServiceIsRefused() {
    dashboard.answer(
        "GET",
        "/api/workstation/services/wbe/reachability",
        404,
        """
        {"type": "about:blank", "title": "Not Found", "status": 404,
         "detail": "This Workstation has no Service wbe."}
        """);

    Run run = yard("test", "wbe");

    assertThat(run.exitCode()).isEqualTo(1);
    assertThat(run.err()).isEqualTo("This Workstation has no Service wbe.\n");
  }

  @Test
  void aliasAddGivesTheRemoteServiceOfTheSameNameItsAlias() {
    dashboard.answer(
        "PUT",
        "/api/workstation/aliases/grafana",
        200,
        """
        {"name": "grafana", "workstation": "ws-b", "service": "grafana", "hostname": "grafana.lvh.me",
         "state": "active"}
        """);

    Run run = yard("alias", "add", "grafana", "--workstation", "ws-b");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out()).isEqualTo("grafana.lvh.me now reaches grafana on ws-b.\n");
    dashboard.verifyCalled(
        "PUT",
        "/api/workstation/aliases/grafana",
        """
        {"workstation": "ws-b", "service": "grafana"}
        """);
  }

  @Test
  void aliasAddWithoutARouteSaysWhy() {
    dashboard.answer(
        "PUT",
        "/api/workstation/aliases/db",
        200,
        """
        {"name": "db", "workstation": "ws-b", "service": "postgres", "hostname": "db.lvh.me",
         "state": "not-shared", "reason": "ws-b publishes postgres with Local Scope, so only ws-b routes it."}
        """);

    Run run = yard("alias", "add", "db", "--workstation", "ws-b", "--service", "postgres");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out())
        .isEqualTo("db.lvh.me names postgres on ws-b, but has no Route for now.\n");
    assertThat(run.err())
        .isEqualTo("Warning: ws-b publishes postgres with Local Scope, so only ws-b routes it.\n");
    dashboard.verifyCalled(
        "PUT",
        "/api/workstation/aliases/db",
        """
        {"workstation": "ws-b", "service": "postgres"}
        """);
  }

  @Test
  void aliasAddNeedsTheWorkstation() {
    Run run = yard("alias", "add", "grafana");

    assertThat(run.exitCode()).isEqualTo(CommandLine.ExitCode.USAGE);
    assertThat(run.err()).contains("--workstation");
  }

  @Test
  void aliasAddOfAServiceNotInTheYardKeepsTheAliasAndSaysWhy() {
    dashboard.answer(
        "PUT",
        "/api/workstation/aliases/grafana",
        200,
        """
        {"name": "grafana", "workstation": "ws-b", "service": "grafana", "hostname": "grafana.lvh.me",
         "state": "unreachable", "reason": "Workstation ws-b is unreachable: its Consul does not answer."}
        """);

    Run run = yard("alias", "add", "grafana", "--workstation", "ws-b");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out())
        .isEqualTo("grafana.lvh.me names grafana on ws-b, but has no Route for now.\n");
    assertThat(run.err())
        .isEqualTo("Warning: Workstation ws-b is unreachable: its Consul does not answer.\n");
  }

  @Test
  void aliasListShowsEachAliasWithItsServiceAndState() {
    dashboard.answer(
        "GET",
        "/api/workstation/aliases",
        200,
        """
        [{"name": "db", "workstation": "ws-b", "service": "postgres", "hostname": "db.lvh.me",
          "state": "fallback",
          "reason": "The last fallback for this Workstation's Service db: used when none of its Instances passes."},
         {"name": "grafana", "workstation": "ws-b", "service": "grafana", "hostname": "grafana.lvh.me",
          "state": "active"}]
        """);

    Run run = yard("alias", "list");

    assertThat(run.exitCode()).isZero();
    assertThat(run.lines())
        .containsExactly(
            "db.lvh.me: postgres on ws-b, fallback",
            "  The last fallback for this Workstation's Service db: used when none of its Instances"
                + " passes.",
            "grafana.lvh.me: grafana on ws-b, active");
  }

  @Test
  void aliasListWithoutAliasesSaysSo() {
    dashboard.answer("GET", "/api/workstation/aliases", 200, "[]");

    Run run = yard("alias", "list");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out()).isEqualTo("No Aliases.\n");
  }

  @Test
  void aliasRemoveRemovesTheAlias() {
    dashboard.answer("DELETE", "/api/workstation/aliases/grafana", 204, null);

    Run run = yard("alias", "remove", "grafana");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out()).isEqualTo("Removed the Alias grafana.\n");
    dashboard.verifyCalled("DELETE", "/api/workstation/aliases/grafana");
  }

  @Test
  void refusalIsPrintedWithItsReason() {
    dashboard.answer(
        "POST",
        "/api/workstation/services/Web/instances",
        400,
        """
        {"type": "about:blank", "title": "Bad Request", "status": 400,
         "detail": "The Service name Web is not a single DNS label."}
        """);

    Run run = yard("register", "static", "Web", "--port", "8080");

    assertThat(run.exitCode()).isNotZero();
    assertThat(run.out()).isEmpty();
    assertThat(run.err()).isEqualTo("The Service name Web is not a single DNS label.\n");
  }

  @Test
  void dashboardThatDoesNotAnswerIsExplained() throws IOException {
    String nothingListens = "http://127.0.0.1:" + freePort();

    Run run = yardAsTyped("--dashboard", nothingListens, "list");

    assertThat(run.exitCode()).isNotZero();
    assertThat(run.err())
        .contains("The dashboard does not answer at")
        .contains(nothingListens)
        .contains("docker compose up -d")
        .doesNotContain("mise");
  }

  @Test
  void serverThatIsNotTheDashboardIsExplained() throws IOException {
    HttpServer other =
        HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    other.createContext(
        "/",
        exchange -> {
          byte[] page = "<html>Not LVH Yard</html>".getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, page.length);
          exchange.getResponseBody().write(page);
          exchange.close();
        });
    other.start();
    try {
      Run run =
          yardAsTyped("--dashboard", "http://127.0.0.1:" + other.getAddress().getPort(), "list");

      assertThat(run.exitCode()).isNotZero();
      assertThat(run.err()).contains("is not the LVH Yard dashboard");
    } finally {
      other.stop(0);
    }
  }

  @Test
  void dashboardUrlComesFromTheEnvironment() throws Exception {
    dashboard.answer(
        "GET",
        "/api/yard",
        200,
        """
        {"home": {"name": "ws-a", "services": []}, "remotes": []}
        """);

    Run run = yardProcess(Map.of("YARD_DASHBOARD_URL", dashboard.url().toString()), "list");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out()).isEqualTo("ws-a (this Workstation): no Services\n");
  }

  @Test
  void dashboardOptionWinsOverTheEnvironment() throws Exception {
    dashboard.answer(
        "GET",
        "/api/yard",
        200,
        """
        {"home": {"name": "ws-a", "services": []}, "remotes": []}
        """);

    Run run =
        yardProcess(
            Map.of("YARD_DASHBOARD_URL", "http://127.0.0.1:" + freePort()),
            "--dashboard",
            dashboard.url().toString(),
            "list");

    assertThat(run.exitCode()).isZero();
  }

  @Test
  void dashboardUrlDefaultsToLoopback() throws Exception {
    Run run = yardProcess(Map.of(), "--help");

    assertThat(run.out()).contains("else http://127.0.0.1:7780.");
  }

  @Test
  void dashboardUrlComesFromTheEnvThatInitWrote(@TempDir Path config) throws Exception {
    answerAnEmptyYard();
    Path folder = config.resolve("lvh-yard");
    yardAsTyped("init", "--dir", folder.toString(), "--workstation", "ws-a");
    setInEnv(folder, "YARD_DASHBOARD_PORT", String.valueOf(dashboard.url().getPort()));

    Run run = yardProcess(Map.of("XDG_CONFIG_HOME", config.toString()), "list");

    assertThat(run.exitCode()).as(run.err()).isZero();
    assertThat(run.out()).isEqualTo("ws-a (this Workstation): no Services\n");
  }

  @Test
  void aWildcardBindAddressInTheEnvIsReachedThroughLoopback(@TempDir Path config) throws Exception {
    Path folder = config.resolve("lvh-yard");
    yardAsTyped("init", "--dir", folder.toString(), "--workstation", "ws-a");
    int port = freePort();
    setInEnv(folder, "YARD_DASHBOARD_BIND_ADDRESS", "0.0.0.0");
    setInEnv(folder, "YARD_DASHBOARD_PORT", String.valueOf(port));

    Run run = yardProcess(Map.of("XDG_CONFIG_HOME", config.toString()), "list");

    assertThat(run.exitCode()).isNotZero();
    assertThat(run.err()).contains("does not answer at http://127.0.0.1:" + port);
  }

  @Test
  void dashboardUrlComesFromTheEnvInYardConfigDir(@TempDir Path folder, @TempDir Path config)
      throws Exception {
    answerAnEmptyYard();
    yardAsTyped("init", "--dir", folder.toString(), "--workstation", "ws-a");
    setInEnv(folder, "YARD_DASHBOARD_PORT", String.valueOf(dashboard.url().getPort()));
    Path standard = config.resolve("lvh-yard");
    yardAsTyped("init", "--dir", standard.toString(), "--workstation", "ws-a");
    setInEnv(standard, "YARD_DASHBOARD_PORT", String.valueOf(freePort()));

    Run run =
        yardProcess(
            Map.of("YARD_CONFIG_DIR", folder.toString(), "XDG_CONFIG_HOME", config.toString()),
            "list");

    assertThat(run.exitCode()).as(run.err()).isZero();
  }

  @Test
  void dashboardUrlFromTheEnvironmentWinsOverTheEnv(@TempDir Path config) throws Exception {
    answerAnEmptyYard();
    Path folder = config.resolve("lvh-yard");
    yardAsTyped("init", "--dir", folder.toString(), "--workstation", "ws-a");
    setInEnv(folder, "YARD_DASHBOARD_PORT", String.valueOf(freePort()));

    Run run =
        yardProcess(
            Map.of(
                "XDG_CONFIG_HOME",
                config.toString(),
                "YARD_DASHBOARD_URL",
                dashboard.url().toString()),
            "list");

    assertThat(run.exitCode()).as(run.err()).isZero();
  }

  @Test
  void anUnreadableEnvFallsThroughToLoopback(@TempDir Path config) throws Exception {
    Files.createDirectories(config.resolve("lvh-yard/.env"));

    Run run = yardProcess(Map.of("XDG_CONFIG_HOME", config.toString()), "list");

    // A dashboard of this computer's own may answer at the default; else the error names it.
    assertThat(run.exitCode() == 0 || run.err().contains("at http://127.0.0.1:7780"))
        .as(run.err())
        .isTrue();
  }

  @Test
  void withoutAnEnvTheDashboardIsOnLoopback() throws Exception {
    Run run = yardProcess(Map.of(), "list");

    // A dashboard of this computer's own may answer at the default; else the error names it.
    assertThat(run.exitCode() == 0 || run.err().contains("at http://127.0.0.1:7780"))
        .as(run.err())
        .isTrue();
  }

  @Test
  void helpShowsTheOrderTheDashboardIsFoundIn() throws Exception {
    Run run = yardProcess(Map.of(), "--help");

    assertThat(run.out())
        .containsSubsequence(
            "--dashboard=<url>",
            "YARD_DASHBOARD_URL",
            "YARD_CONFIG_DIR",
            "~/.config/lvh-yard",
            "http://127.0.0.1:7780");
  }

  @Test
  void initWritesComposeFileAndEnvWithTheWorkstationIntoDir(@TempDir Path dir) throws Exception {
    Run run = yardAsTyped("init", "--dir", dir.toString(), "--workstation", "ws-a");

    assertThat(run.exitCode()).isZero();
    assertThat(dir.resolve("compose.yaml")).exists();
    assertThat(env(dir)).contains("YARD_WORKSTATION=ws-a");
  }

  @Test
  void initWritesToXdgConfigHomeByDefault(@TempDir Path config) throws Exception {
    Run run =
        yardProcess(Map.of("XDG_CONFIG_HOME", config.toString()), "init", "--workstation", "ws-a");

    assertThat(run.exitCode()).isZero();
    assertThat(config.resolve("lvh-yard/compose.yaml")).exists();
    assertThat(config.resolve("lvh-yard/.env")).exists();
    assertThat(config.resolve("lvh-yard/route-profiles.yaml")).doesNotExist();
  }

  @Test
  void initWritesToDotConfigInHomeWithoutXdgConfigHome(@TempDir Path home) throws Exception {
    Run run = yardProcess(Map.of("HOME", home.toString()), "init", "--workstation", "ws-a");

    assertThat(run.exitCode()).isZero();
    assertThat(home.resolve(".config/lvh-yard/compose.yaml")).exists();
  }

  @Test
  void initsComposeFileIsTheRepositorysWithTheDashboardImagePinnedAndNothingToBuild(
      @TempDir Path dir) throws Exception {
    yardAsTyped("init", "--dir", dir.toString(), "--workstation", "ws-a");

    // A snapshot build has no image of its own; a release pins its version.
    String tag = Version.VERSION.endsWith("-SNAPSHOT") ? "latest" : Version.VERSION;
    List<String> repository = Files.readAllLines(Path.of("..", "compose.yaml"));
    List<String> expected = new ArrayList<>();
    for (String line : repository) {
      if (line.equals("    build: .")) {
        continue;
      }
      expected.add(
          line.equals(
                  "    image: ${YARD_DASHBOARD_IMAGE:-ghcr.io/ppalazon/lvh-yard-dashboard:latest}")
              ? "    image: ${YARD_DASHBOARD_IMAGE:-ghcr.io/ppalazon/lvh-yard-dashboard:"
                  + tag
                  + "}"
              : line);
    }
    assertThat(expected).hasSize(repository.size() - 1);
    assertThat(Files.readAllLines(dir.resolve("compose.yaml"))).isEqualTo(expected);
  }

  @Test
  void initsEnvIsTheRepositorysEnvExampleLineByLineWithItsCommentsKept(@TempDir Path dir)
      throws Exception {
    yardAsTyped("init", "--dir", dir.toString(), "--workstation", "ws-a");

    List<String> example = Files.readAllLines(Path.of("..", ".env.example"));
    List<String> written = env(dir);
    assertThat(written).hasSameSizeAs(example);
    for (int i = 0; i < example.size(); i++) {
      String line = example.get(i);
      if (line.matches("#?[A-Z_]+=.*")) {
        String key = line.replaceFirst("^#?([A-Z_]+)=.*", "$1");
        assertThat(written.get(i)).as("line %d", i + 1).matches("#?" + key + "=.*");
      } else {
        assertThat(written.get(i)).as("line %d", i + 1).isEqualTo(line);
      }
    }
  }

  @Test
  void initFillsEnvFromItsFlags(@TempDir Path dir) throws Exception {
    String key = "pUqJrVyVRj5jsiYEkM/tFQYfWyJIv4s3XkvDwy7Cu5s=";
    Run run =
        yardAsTyped(
            "init",
            "--dir",
            dir.toString(),
            "--workstation",
            "ws-b",
            "--peers",
            "192.168.1.10,192.168.1.11:8302",
            "--gossip-key",
            key,
            "--base-domain",
            "localhost");

    assertThat(run.exitCode()).isZero();
    assertThat(env(dir))
        .contains(
            "YARD_WORKSTATION=ws-b",
            "YARD_PEERS=192.168.1.10 192.168.1.11:8302",
            "YARD_GOSSIP_KEY=" + key,
            "YARD_BASE_DOMAIN=localhost",
            "COMPOSE_PROFILES=dashboard");
  }

  @Test
  void initWithoutPeersLeavesThemEmptyForTheFirstWorkstation(@TempDir Path dir) throws Exception {
    yardAsTyped("init", "--dir", dir.toString(), "--workstation", "ws-a");

    assertThat(env(dir)).contains("YARD_PEERS=", "YARD_BASE_DOMAIN=lvh.me");
  }

  @Test
  void initRefusesAWorkstationNameTheDashboardWouldNot(@TempDir Path dir) {
    Run run = yardAsTyped("init", "--dir", dir.toString(), "--workstation", "WS_A");

    assertThat(run.exitCode()).isEqualTo(2);
    assertThat(run.err()).contains("--workstation WS_A is not a Workstation name");
    assertThat(dir).isEmptyDirectory();
  }

  @Test
  void initWithoutAWorkstationAndNoTerminalNamesTheFlag(@TempDir Path dir) throws Exception {
    Run run = yardProcess(Map.of(), "init", "--dir", dir.toString());

    assertThat(run.exitCode()).isEqualTo(2);
    assertThat(run.err()).contains("Missing --workstation");
    assertThat(dir).isEmptyDirectory();
  }

  @Test
  void initInATerminalAsksForTheWorkstationAndTheBaseDomainButNotThePeers(@TempDir Path dir)
      throws Exception {
    Run run = yardAtTerminal("ws-a\nexample.test\n", "init", "--dir", dir.toString());

    assertThat(run.exitCode()).isZero();
    assertThat(run.out()).contains("Workstation name", "Base domain [lvh.me]: ");
    assertThat(run.out()).doesNotContain("Peers");
    assertThat(env(dir))
        .contains("YARD_WORKSTATION=ws-a", "YARD_BASE_DOMAIN=example.test", "YARD_PEERS=");
  }

  @Test
  void initInATerminalOffersTheHostnameAsTheWorkstationName(@TempDir Path dir) throws Exception {
    Run run =
        yardAtTerminal(
            "\n\n", "init", "--dir", dir.toString(), "--hostname", "Pablo_Laptop.local\n");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out()).contains("[pablo-laptop]: ");
    assertThat(env(dir)).contains("YARD_WORKSTATION=pablo-laptop", "YARD_BASE_DOMAIN=lvh.me");
  }

  @Test
  void initInATerminalOffersNoNameForAHostnameWithNothingValid(@TempDir Path dir) throws Exception {
    Run run = yardAtTerminal("ws-a\n\n", "init", "--dir", dir.toString(), "--hostname", "___");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out()).contains("(lowercase letters, digits and -): ");
    assertThat(env(dir)).contains("YARD_WORKSTATION=ws-a");
  }

  @Test
  void initMakesAWorkstationNameFromAHostname() {
    assertThat(Init.nameFromHostname("My-Box.example.com")).contains("my-box");
    assertThat(Init.nameFromHostname("-dev_box-")).contains("dev-box");
    assertThat(Init.nameFromHostname("a".repeat(62) + "-b")).contains("a".repeat(62));
    assertThat(Init.nameFromHostname("")).isEmpty();
    assertThat(Init.nameFromHostname(null)).isEmpty();
  }

  @Test
  void initInATerminalExplainsAnInvalidWorkstationNameAndAsksAgain(@TempDir Path dir)
      throws Exception {
    Run run =
        yardAtTerminal("WS_A\n\nws-a\n\n", "init", "--dir", dir.toString(), "--hostname", "_");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out()).contains("WS_A is not a Workstation name", "A Workstation needs a name");
    assertThat(run.out().split("Workstation name, unique", -1)).hasSize(4);
    assertThat(env(dir)).contains("YARD_WORKSTATION=ws-a");
  }

  @Test
  void initInATerminalExplainsAnInvalidBaseDomainAndAsksAgain(@TempDir Path dir) throws Exception {
    Run run = yardAtTerminal("ws-a\nNot A Domain\nlocalhost\n", "init", "--dir", dir.toString());

    assertThat(run.exitCode()).isZero();
    assertThat(run.out()).contains("Not A Domain is not a domain name");
    assertThat(env(dir)).contains("YARD_BASE_DOMAIN=localhost");
  }

  @Test
  void initInATerminalAsksOnlyForWhatNoFlagGave(@TempDir Path dir) throws Exception {
    Run run =
        yardAtTerminal(
            "",
            "init",
            "--dir",
            dir.toString(),
            "--workstation",
            "ws-a",
            "--base-domain",
            "lvh.me");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out()).doesNotContain("Workstation name", "Base domain");
  }

  @Test
  void initWithReinitInATerminalAsksNothing(@TempDir Path dir) throws Exception {
    yardAsTyped(
        "init", "--dir", dir.toString(), "--workstation", "ws-a", "--base-domain", "x.test");

    Run run = yardAtTerminal("", "init", "--dir", dir.toString(), "--reinit");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out()).doesNotContain("Workstation name", "Base domain");
    assertThat(env(dir)).contains("YARD_WORKSTATION=ws-a", "YARD_BASE_DOMAIN=x.test");
  }

  @Test
  void initWithoutATerminalNeverAsks(@TempDir Path dir) throws Exception {
    Run run = yardProcess(Map.of(), "init", "--dir", dir.toString(), "--workstation", "ws-a");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out()).doesNotContain("Workstation name", "Base domain");
    assertThat(env(dir)).contains("YARD_PEERS=", "YARD_BASE_DOMAIN=lvh.me");
  }

  @Test
  void initInATerminalWhoseInputEndsNamesTheFlag(@TempDir Path dir) throws Exception {
    Run run = yardAtTerminal("", "init", "--dir", dir.toString());

    assertThat(run.exitCode()).isEqualTo(2);
    assertThat(run.err()).contains("Missing --workstation");
    assertThat(dir).isEmptyDirectory();
  }

  @Test
  void initMakesANewGossipKeyAndSaysToShareIt(@TempDir Path dir) throws Exception {
    Run run = yardAsTyped("init", "--dir", dir.toString(), "--workstation", "ws-a");

    String key = setting(dir, "YARD_GOSSIP_KEY");
    assertThat(Base64.getDecoder().decode(key)).hasSize(32);
    assertThat(run.out())
        .contains("The Yard's gossip key: " + key)
        .contains("Give this key to every other Workstation");
    yardAsTyped("init", "--dir", dir.toString(), "--workstation", "ws-a", "--force");
    assertThat(setting(dir, "YARD_GOSSIP_KEY")).isNotEqualTo(key);
  }

  @Test
  void initRefusesAGossipKeyConsulWouldNot(@TempDir Path dir) {
    Run run =
        yardAsTyped(
            "init", "--dir", dir.toString(), "--workstation", "ws-a", "--gossip-key", "secret");

    assertThat(run.exitCode()).isEqualTo(2);
    assertThat(run.err()).contains("--gossip-key is not a gossip key");
  }

  @Test
  void initFillsTheUserGroupAndDockerGroupFromThisComputer(@TempDir Path dir) throws Exception {
    Path socket = Files.createFile(dir.resolve("docker.sock"));
    Path folder = dir.resolve("yard");

    Run run =
        yardAsTyped(
            "init",
            "--dir",
            folder.toString(),
            "--workstation",
            "ws-a",
            "--docker-socket",
            socket.toString());

    assertThat(run.exitCode()).isZero();
    assertThat(run.err()).isEmpty();
    assertThat(env(folder))
        .contains(
            "YARD_UID=" + Files.getAttribute(socket, "unix:uid"),
            "YARD_GID=" + Files.getAttribute(socket, "unix:gid"),
            "YARD_DOCKER_GID=" + Files.getAttribute(socket, "unix:gid"));
  }

  @Test
  void initWithoutADockerSocketWarnsAndStillWritesTheFiles(@TempDir Path dir) throws Exception {
    Run run =
        yardAsTyped(
            "init",
            "--dir",
            dir.toString(),
            "--workstation",
            "ws-a",
            "--docker-socket",
            dir.resolve("no-docker.sock").toString());

    assertThat(run.exitCode()).isZero();
    assertThat(run.err()).contains("Warning: no Docker socket at").contains("YARD_DOCKER_GID");
    assertThat(env(dir)).contains("#YARD_DOCKER_GID=");
  }

  @Test
  void initAndReinitWriteNoRouteProfilesFile(@TempDir Path dir) throws Exception {
    yardAsTyped("init", "--dir", dir.toString(), "--workstation", "ws-a");
    yardAsTyped("init", "--dir", dir.toString(), "--reinit");

    assertThat(dir.resolve("route-profiles.yaml")).doesNotExist();
  }

  @Test
  void initNeverOverwritesAFileWithoutForce(@TempDir Path dir) throws Exception {
    Files.writeString(dir.resolve(".env"), "YARD_WORKSTATION=mine\n");

    Run run = yardAsTyped("init", "--dir", dir.toString(), "--workstation", "ws-a");

    assertThat(run.exitCode()).isEqualTo(1);
    assertThat(run.err()).contains(dir.resolve(".env") + " already exists").contains("--force");
    assertThat(dir.resolve(".env")).hasContent("YARD_WORKSTATION=mine");
    assertThat(dir.resolve("compose.yaml")).doesNotExist();
  }

  @Test
  void initWithForceOverwritesTheFiles(@TempDir Path dir) throws Exception {
    Files.writeString(dir.resolve(".env"), "YARD_WORKSTATION=mine\n");

    Run run = yardAsTyped("init", "--dir", dir.toString(), "--workstation", "ws-a", "--force");

    assertThat(run.exitCode()).isZero();
    assertThat(env(dir)).contains("YARD_WORKSTATION=ws-a");
  }

  @Test
  void initWithReinitBacksUpComposeFileAndEnvAndNamesTheBackups(@TempDir Path dir)
      throws Exception {
    Files.writeString(dir.resolve("compose.yaml"), "old compose\n");
    Files.writeString(dir.resolve(".env"), "YARD_WORKSTATION=ws-a\n");

    Run run = yardAsTyped("init", "--dir", dir.toString(), "--reinit");

    assertThat(run.exitCode()).as(run.err()).isZero();
    List<Path> backups = backups(dir);
    assertThat(backups)
        .extracting(path -> path.getFileName().toString())
        .hasSize(2)
        .anyMatch(name -> name.matches("compose\\.yaml\\.\\d{8}-\\d{6}\\.bak"))
        .anyMatch(name -> name.matches("\\.env\\.\\d{8}-\\d{6}\\.bak"));
    for (Path backup : backups) {
      assertThat(run.out()).contains(backup.toString());
    }
    assertThat(backups)
        .filteredOn(path -> path.getFileName().toString().startsWith("compose"))
        .singleElement()
        .satisfies(path -> assertThat(path).hasContent("old compose"));
    assertThat(Files.readString(dir.resolve("compose.yaml"))).contains("lvh-yard-dashboard:");
  }

  @Test
  void initWithReinitCarriesOverTheOldValuesAndGivesNewSettingsTheirDefault(@TempDir Path dir)
      throws Exception {
    String key = "pUqJrVyVRj5jsiYEkM/tFQYfWyJIv4s3XkvDwy7Cu5s=";
    Files.writeString(
        dir.resolve(".env"),
        """
        # An older .env, without most of today's settings.
        YARD_WORKSTATION=ws-b
        YARD_GOSSIP_KEY=%s
        YARD_PEERS=10.0.0.2 10.0.0.3:8302
        YARD_DASHBOARD_PORT=7790
        #YARD_HTTP_PORT=81
        """
            .formatted(key));

    Run run = yardAsTyped("init", "--dir", dir.toString(), "--reinit");

    assertThat(run.exitCode()).as(run.err()).isZero();
    assertThat(env(dir))
        .contains(
            "YARD_WORKSTATION=ws-b",
            "YARD_GOSSIP_KEY=" + key,
            "YARD_PEERS=10.0.0.2 10.0.0.3:8302",
            "YARD_DASHBOARD_PORT=7790",
            "YARD_HTTP_PORT=80",
            "YARD_BASE_DOMAIN=lvh.me",
            "YARD_CONSUL_SERVER_PORT=8300");
    assertThat(run.out())
        .contains("The Yard's gossip key: " + key)
        .contains("http://127.0.0.1:7790");
  }

  @Test
  void initWithReinitKeepsWhatItFindsOverAnOldEmptyValue(@TempDir Path dir) throws Exception {
    Path socket = Files.createFile(dir.resolve("docker.sock"));
    Path folder = Files.createDirectory(dir.resolve("yard"));
    Files.writeString(
        folder.resolve(".env"), "YARD_WORKSTATION=ws-a\nYARD_UID=\nYARD_GID=\nYARD_DOCKER_GID=\n");

    Run run =
        yardAsTyped(
            "init", "--dir", folder.toString(), "--reinit", "--docker-socket", socket.toString());

    assertThat(run.exitCode()).as(run.err()).isZero();
    assertThat(run.err()).isEmpty();
    assertThat(env(folder))
        .contains(
            "YARD_UID=" + Files.getAttribute(socket, "unix:uid"),
            "YARD_GID=" + Files.getAttribute(socket, "unix:gid"),
            "YARD_DOCKER_GID=" + Files.getAttribute(socket, "unix:gid"));
  }

  @Test
  void initsNextStepsAndListAgreeOnTheDashboardForABlankBindAddressAndPort(@TempDir Path config)
      throws Exception {
    Path folder = Files.createDirectories(config.resolve("lvh-yard"));
    Files.writeString(
        folder.resolve(".env"),
        "YARD_WORKSTATION=ws-a\nYARD_DASHBOARD_BIND_ADDRESS= \nYARD_DASHBOARD_PORT= \n");

    Run init = yardAsTyped("init", "--dir", folder.toString(), "--reinit");
    Run list = yardProcess(Map.of("XDG_CONFIG_HOME", config.toString()), "list");

    assertThat(init.out())
        .contains("Open the dashboard: http://127.0.0.1:7780\n")
        .contains("  4. Make sure that LVH Yard works: lvh-yard status\n\n");
    // A dashboard of this computer's own may answer at the default; else the error names it.
    assertThat(list.exitCode() == 0 || list.err().contains("at http://127.0.0.1:7780"))
        .as(list.err())
        .isTrue();
  }

  @Test
  void initWithReinitKeepsOldKeysThatNoLongerExistCommentedAtTheEnd(@TempDir Path dir)
      throws Exception {
    Files.writeString(
        dir.resolve(".env"), "YARD_WORKSTATION=ws-a\nYARD_GONE=1\nYARD_ALSO_GONE=a b\n");

    yardAsTyped("init", "--dir", dir.toString(), "--reinit");

    List<String> env = env(dir);
    assertThat(env.subList(env.size() - 3, env.size()))
        .containsExactly(
            "# No longer settings of lvh-yard, kept from the old .env:",
            "#YARD_GONE=1",
            "#YARD_ALSO_GONE=a b");
    assertThat(env).doesNotContain("YARD_GONE=1");
  }

  @Test
  void initWithReinitLeavesRouteProfilesUntouched(@TempDir Path dir) throws Exception {
    Files.writeString(dir.resolve(".env"), "YARD_WORKSTATION=ws-a\n");
    Files.writeString(dir.resolve("route-profiles.yaml"), "mine: {}\n");

    yardAsTyped("init", "--dir", dir.toString(), "--reinit");

    assertThat(dir.resolve("route-profiles.yaml")).hasContent("mine: {}");
    assertThat(backups(dir))
        .extracting(path -> path.getFileName().toString())
        .noneMatch(name -> name.startsWith("route-profiles"));
  }

  @Test
  void initWithReinitLetsAFlagReplaceAnOldValue(@TempDir Path dir) throws Exception {
    Files.writeString(dir.resolve(".env"), "YARD_WORKSTATION=ws-a\nYARD_BASE_DOMAIN=lvh.me\n");

    yardAsTyped("init", "--dir", dir.toString(), "--reinit", "--base-domain", "localhost");

    assertThat(env(dir)).contains("YARD_WORKSTATION=ws-a", "YARD_BASE_DOMAIN=localhost");
  }

  @Test
  void initWithReinitButNoEnvRefusesAndWritesNothing(@TempDir Path dir) throws Exception {
    Files.writeString(dir.resolve("compose.yaml"), "old compose\n");

    Run run = yardAsTyped("init", "--dir", dir.toString(), "--reinit", "--workstation", "ws-a");

    assertThat(run.exitCode()).isEqualTo(1);
    assertThat(run.err())
        .contains("No " + dir.resolve(".env") + " to carry values from")
        .contains("without --reinit");
    try (var files = Files.list(dir)) {
      assertThat(files).containsExactly(dir.resolve("compose.yaml"));
    }
    assertThat(dir.resolve("compose.yaml")).hasContent("old compose");
  }

  @Test
  void initPrintsTheNextSteps(@TempDir Path dir) {
    Run run = yardAsTyped("init", "--dir", dir.toString(), "--workstation", "ws-a");

    assertThat(run.out())
        .contains("Open TCP port 8300 and TCP and UDP port 8302 to the other Workstations.")
        .contains("Start this Workstation: cd " + dir + " && docker compose up -d")
        .contains("http://127.0.0.1:7780")
        .contains("lvh-yard status")
        .contains("lvh-yard init --reinit --peers <ip> --gossip-key <key>");
  }

  @Test
  void initsFolderIsAValidComposeProject(@TempDir Path dir) throws Exception {
    assumeTrue(dockerCompose(), "docker compose is not installed");
    yardAsTyped("init", "--dir", dir.toString(), "--workstation", "ws-a", "--peers", "10.0.0.2");

    ProcessBuilder config =
        new ProcessBuilder("docker", "compose", "config", "--format", "json")
            .directory(dir.toFile());
    config
        .environment()
        .keySet()
        .removeIf(name -> name.startsWith("YARD_") || name.startsWith("COMPOSE_"));
    Run run = run(config);

    assertThat(run.exitCode()).as(run.err()).isZero();
    assertThat(run.out())
        .contains("\"YARD_WORKSTATION\": \"ws-a\"")
        .contains("\"YARD_PEERS\": \"10.0.0.2\"")
        .contains("\"dashboard\": {");
  }

  @Test
  void initsDashboardWritesOnlyToTheBackupsFolder(@TempDir Path dir) throws Exception {
    assumeTrue(dockerCompose(), "docker compose is not installed");
    yardAsTyped("init", "--dir", dir.toString(), "--workstation", "ws-a");

    ProcessBuilder config =
        new ProcessBuilder(
                "docker", "compose", "--profile", "dashboard", "config", "--format", "json")
            .directory(dir.toFile());
    config
        .environment()
        .keySet()
        .removeIf(name -> name.startsWith("YARD_") || name.startsWith("COMPOSE_"));
    Run run = run(config);

    assertThat(run.exitCode()).as(run.err()).isZero();
    JsonNode dashboard = new JsonMapper().readTree(run.out()).at("/services/dashboard");
    assertThat(dashboard.at("/environment").has("YARD_ROUTE_PROFILES")).isFalse();
    String backups = dashboard.at("/environment/YARD_BACKUPS").asString();
    assertThat(dashboard.get("volumes").valueStream())
        .noneSatisfy(volume ->
            assertThat(volume.get("source").asString()).isEqualTo(dir.toRealPath().toString()))
        .anySatisfy(volume -> {
          assertThat(volume.get("source").asString())
              .isEqualTo(dir.toRealPath().resolve("backups").toString());
          assertThat(volume.get("target").asString()).isEqualTo(backups);
          assertThat(volume.has("read_only")).isFalse();
        });
  }

  @Test
  void initAndReinitCreateTheBackupsFolder(@TempDir Path dir) throws Exception {
    Run init = yardAsTyped("init", "--dir", dir.toString(), "--workstation", "ws-a");
    assertThat(init.exitCode()).as(init.err()).isZero();
    assertThat(dir.resolve("backups")).isDirectory();

    Files.delete(dir.resolve("backups"));
    Run reinit = yardAsTyped("init", "--dir", dir.toString(), "--reinit");

    assertThat(reinit.exitCode()).as(reinit.err()).isZero();
    assertThat(dir.resolve("backups")).isDirectory();
    assertThat(Files.readString(dir.resolve("compose.yaml"))).contains("./backups:");
  }

  @Test
  void reinitKeepsTheBackupsInTheBackupsFolder(@TempDir Path dir) throws Exception {
    yardAsTyped("init", "--dir", dir.toString(), "--workstation", "ws-a");
    Path backup = dir.resolve("backups").resolve("ws-a-20261105-100000.yaml");
    Files.writeString(backup, "version: 1\n");

    Run run = yardAsTyped("init", "--dir", dir.toString(), "--reinit");

    assertThat(run.exitCode()).as(run.err()).isZero();
    assertThat(backup).hasContent("version: 1");
  }

  @Test
  void initUpgradeMovesOnlyTheThreeImagesToThisVersions(@TempDir Path dir) throws Exception {
    yardAsTyped("init", "--dir", dir.toString(), "--workstation", "ws-a");
    String current = Files.readString(dir.resolve("compose.yaml"));
    String consul = imageOf(current, "hashicorp/consul:");
    String traefik = imageOf(current, "traefik:");
    String dashboard = imageOf(current, "${YARD_DASHBOARD_IMAGE:-");
    String oldDashboard = "${YARD_DASHBOARD_IMAGE:-ghcr.io/ppalazon/lvh-yard-dashboard:0.1.0}";
    // An older release's file, edited by hand.
    String old =
        current
            .replace(consul, "hashicorp/consul:1.0.0")
            .replace(traefik, "traefik:v3.0.0  # pinned by me")
            .replace(dashboard, oldDashboard)
            .replace("services:", "services:\n  # my own note");
    Files.writeString(dir.resolve("compose.yaml"), old);
    Files.writeString(dir.resolve(".env"), "YARD_WORKSTATION=mine\n");
    Files.writeString(dir.resolve("route-profiles.yaml"), "# mine\n");

    Run run = yardAsTyped("init", "--dir", dir.toString(), "--upgrade");

    assertThat(run.exitCode()).as(run.err()).isZero();
    assertThat(Files.readString(dir.resolve("compose.yaml")))
        .isEqualTo(
            current
                .replace(traefik, traefik + "  # pinned by me")
                .replace("services:", "services:\n  # my own note"));
    assertThat(Files.readString(dir.resolve(".env"))).isEqualTo("YARD_WORKSTATION=mine\n");
    assertThat(Files.readString(dir.resolve("route-profiles.yaml"))).isEqualTo("# mine\n");
    assertThat(run.out())
        .contains("consul: hashicorp/consul:1.0.0 -> " + consul)
        .contains("traefik: traefik:v3.0.0 -> " + traefik)
        .contains("dashboard: " + oldDashboard + " -> " + dashboard);
  }

  @Test
  void initUpgradeReportsAMissingImageLineAndLeavesTheFileAsItIs(@TempDir Path dir)
      throws Exception {
    yardAsTyped("init", "--dir", dir.toString(), "--workstation", "ws-a");
    String current = Files.readString(dir.resolve("compose.yaml"));
    String old =
        current
            .replace(imageOf(current, "hashicorp/consul:"), "hashicorp/consul:1.0.0")
            .replace("    image: " + imageOf(current, "traefik:"), "    # image: mine");
    Files.writeString(dir.resolve("compose.yaml"), old);

    Run run = yardAsTyped("init", "--dir", dir.toString(), "--upgrade");

    assertThat(run.exitCode()).isEqualTo(1);
    assertThat(run.err())
        .contains(dir.resolve("compose.yaml") + " has no image: line for traefik")
        .contains("nothing was changed");
    assertThat(Files.readString(dir.resolve("compose.yaml"))).isEqualTo(old);
  }

  @Test
  void initUpgradeWithoutAComposeFileSaysToRunInitFirst(@TempDir Path dir) {
    Run run = yardAsTyped("init", "--dir", dir.toString(), "--upgrade");

    assertThat(run.exitCode()).isEqualTo(1);
    assertThat(run.err())
        .contains(dir.resolve("compose.yaml") + " does not exist")
        .contains("lvh-yard init");
    assertThat(dir.resolve("compose.yaml")).doesNotExist();
  }

  @Test
  void initUpgradeAndForceCannotBeCombined(@TempDir Path dir) {
    Run run =
        yardAsTyped(
            "init", "--dir", dir.toString(), "--workstation", "ws-a", "--upgrade", "--force");

    assertThat(run.exitCode()).isEqualTo(2);
    assertThat(run.err()).contains("--force, --upgrade are mutually exclusive");
    assertThat(dir.resolve("compose.yaml")).doesNotExist();
  }

  @Test
  void initReinitCannotBeCombinedWithForceNorUpgrade(@TempDir Path dir) throws Exception {
    Files.writeString(dir.resolve(".env"), "YARD_WORKSTATION=mine\n");

    for (String other : List.of("--force", "--upgrade")) {
      Run run = yardAsTyped("init", "--dir", dir.toString(), "--reinit", other);

      assertThat(run.exitCode()).as(other).isEqualTo(2);
      assertThat(run.err())
          .containsAnyOf(other + ", --reinit are", "--reinit, " + other + " are")
          .contains("mutually exclusive");
    }
    try (var files = Files.list(dir)) {
      assertThat(files.map(file -> file.getFileName().toString())).containsExactly(".env");
    }
    assertThat(Files.readString(dir.resolve(".env"))).isEqualTo("YARD_WORKSTATION=mine\n");
  }

  /**
   * The value of the first {@code image:} line in {@code compose} that starts with {@code prefix}.
   */
  private static String imageOf(String compose, String prefix) {
    return compose
        .lines()
        .map(String::strip)
        .filter(line -> line.startsWith("image: " + prefix))
        .map(line -> line.substring("image: ".length()))
        .findFirst()
        .orElseThrow();
  }

  private void answerWebWithBindings(String bindings) {
    dashboard.answer(
        "GET",
        "/api/workstation/services/web",
        200,
        "{\"workstation\": \"ws-a\", \"name\": \"web\", \"state\": \"unpublished\", \"warnings\":"
            + " [], \"instances\": [], \"containerBindings\": ["
            + bindings
            + "]}");
  }

  private void answerWebWithInstances(String instances) {
    dashboard.answer(
        "GET",
        "/api/workstation/services/web",
        200,
        "{\"workstation\": \"ws-a\", \"name\": \"web\", \"state\": \"unpublished\", \"warnings\":"
            + " [], \"containerBindings\": [], \"instances\": ["
            + instances
            + "]}");
  }

  /** The lines of the {@code .env} that {@code init} wrote in {@code dir}. */
  private static List<String> env(Path dir) throws IOException {
    return Files.readAllLines(dir.resolve(".env"));
  }

  /** The {@code .bak} files in {@code dir}. */
  private static List<Path> backups(Path dir) throws IOException {
    try (var files = Files.list(dir)) {
      return files.filter(path -> path.getFileName().toString().endsWith(".bak")).toList();
    }
  }

  /** The value of {@code key} in the {@code .env} that {@code init} wrote in {@code dir}. */
  private static String setting(Path dir, String key) throws IOException {
    return env(dir).stream()
        .filter(line -> line.startsWith(key + "="))
        .map(line -> line.substring(key.length() + 1))
        .findFirst()
        .orElseThrow();
  }

  private void answerAnEmptyYard() {
    dashboard.answer(
        "GET",
        "/api/yard",
        200,
        """
        {"home": {"name": "ws-a", "services": []}, "remotes": []}
        """);
  }

  /**
   * Sets {@code key} to {@code value} in the {@code .env} that {@code init} wrote in {@code dir}.
   */
  private static void setInEnv(Path dir, String key, String value) throws IOException {
    Path env = dir.resolve(".env");
    Files.writeString(env, EnvFile.parse(Files.readString(env)).set(key, value).text());
  }

  /** Whether this computer has {@code docker compose}. */
  private static boolean dockerCompose() {
    try {
      return run(new ProcessBuilder("docker", "compose", "version")).exitCode() == 0;
    } catch (IOException | InterruptedException e) {
      return false;
    }
  }

  /** The output of one run of {@code lvh-yard}. */
  record Run(int exitCode, String out, String err) {

    List<String> lines() {
      return out.lines().toList();
    }
  }

  /** Runs {@code lvh-yard} against the stub dashboard. */
  private Run yard(String... args) {
    List<String> withDashboard =
        new ArrayList<>(List.of("--dashboard", dashboard.url().toString()));
    withDashboard.addAll(List.of(args));
    return yardAsTyped(withDashboard.toArray(String[]::new));
  }

  /**
   * Runs {@code lvh-yard} in a process of its own, as a shell does, with {@code environment} in
   * place of any {@code YARD_*} variables of this one, and {@link #home} as HOME unless {@code
   * environment} names another.
   */
  private Run yardProcess(Map<String, String> environment, String... args) throws Exception {
    ProcessBuilder builder = new ProcessBuilder(command(args));
    builder
        .environment()
        .keySet()
        .removeIf(name -> name.startsWith("YARD_") || name.equals("XDG_CONFIG_HOME"));
    builder.environment().put("HOME", home.toString());
    builder.environment().putAll(environment);
    return run(builder);
  }

  /**
   * Runs {@code lvh-yard} against the stub dashboard in a process of its own, with {@code editor},
   * a shell script that gets the file as $1, as $EDITOR, and {@code input} on its standard input.
   */
  private Run yardWithEditor(String editor, String input, String... args) throws Exception {
    Path script = Files.createTempFile(home, "editor", ".sh");
    Files.writeString(script, "#!/bin/sh\n" + editor);
    script.toFile().setExecutable(true);
    List<String> withDashboard =
        new ArrayList<>(List.of("--dashboard", dashboard.url().toString()));
    withDashboard.addAll(List.of(args));
    ProcessBuilder builder = new ProcessBuilder(command(withDashboard.toArray(String[]::new)));
    builder.environment().put("HOME", home.toString());
    builder.environment().put("EDITOR", script.toString());
    Process process = builder.start();
    try (var in = process.getOutputStream()) {
      in.write(input.getBytes(StandardCharsets.UTF_8));
    }
    String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    String err = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
    return new Run(process.waitFor(), out, err);
  }

  /** Runs {@code lvh-yard} in a process of its own, with {@code input} on its standard input. */
  private static Run yardWithInput(String input, String... args) throws Exception {
    Process process = new ProcessBuilder(command(args)).start();
    try (var in = process.getOutputStream()) {
      in.write(input.getBytes(StandardCharsets.UTF_8));
    }
    String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    String err = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
    return new Run(process.waitFor(), out, err);
  }

  /**
   * Runs {@code lvh-yard} in a process of its own as if at a terminal, with {@code answers} typed
   * in.
   */
  private static Run yardAtTerminal(String answers, String... args) throws Exception {
    List<String> withTerminal = new ArrayList<>(List.of(args));
    withTerminal.add("--terminal");
    Process process = new ProcessBuilder(command(withTerminal.toArray(String[]::new))).start();
    try (var in = process.getOutputStream()) {
      in.write(answers.getBytes(StandardCharsets.UTF_8));
    }
    String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    String err = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
    return new Run(process.waitFor(), out, err);
  }

  /**
   * {@code lvh-yard} with {@code args}: the native executable when given, else a JVM of its own.
   */
  private static List<String> command(String... args) {
    List<String> command =
        NATIVE_YARD != null
            ? new ArrayList<>(List.of(NATIVE_YARD))
            : new ArrayList<>(
                List.of(
                    ProcessHandle.current().info().command().orElseThrow(),
                    "-cp",
                    System.getProperty("java.class.path"),
                    Yard.class.getName()));
    command.addAll(List.of(args));
    return command;
  }

  private static Run run(ProcessBuilder builder) throws IOException, InterruptedException {
    Process process = builder.start();
    String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    String err = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
    return new Run(process.waitFor(), out, err);
  }

  /** A loopback port nothing listens on. */
  private static int freePort() throws IOException {
    try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      return socket.getLocalPort();
    }
  }

  /** Runs {@code lvh-yard} with exactly {@code args}. */
  private static Run yardAsTyped(String... args) {
    if (NATIVE_YARD != null) {
      try {
        return run(new ProcessBuilder(command(args)));
      } catch (IOException | InterruptedException e) {
        throw new IllegalStateException("Could not run " + NATIVE_YARD, e);
      }
    }
    StringWriter out = new StringWriter();
    StringWriter err = new StringWriter();
    CommandLine command = Yard.commandLine();
    command.setOut(new PrintWriter(out));
    command.setErr(new PrintWriter(err));
    int exitCode = command.execute(args);
    return new Run(exitCode, out.toString(), err.toString());
  }
}
