package dev.palazon.lvhyard.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The CLI tests that run lvh-yard in a process of its own: environment, HOME, $EDITOR, standard
 * input and a terminal.
 */
class YardProcessIT extends YardTestSupport {

  @Test
  void importReadsStandardInputForADash() throws Exception {
    dashboard.answer("POST", "/api/workstation/backup?mode=add", 200, PLAN.formatted(false));

    Run run = yardWithInput(BACKUP, "--dashboard", dashboard.url().toString(), "import", "-");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out()).isEqualTo(PLAN_LINES + "Added 2, skipped 2.\n");
    dashboard.verifyCalled("POST", "/api/workstation/backup", "application/yaml", BACKUP);
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
  void dashboardUrlComesFromTheEnvironment() throws Exception {
    dashboard.answer(
        "GET",
        "/api/yard",
        200,
        """
        {"home": {"name": "ws-a", "services": []}, "remotes": []}
        """);

    Run run = yardProcess(Map.of("YARD_DASHBOARD_URL", dashboard.url().toString()), "catalog");

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
            "catalog");

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

    Run run = yardProcess(Map.of("XDG_CONFIG_HOME", config.toString()), "catalog");

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

    Run run = yardProcess(Map.of("XDG_CONFIG_HOME", config.toString()), "catalog");

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
            "catalog");

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
            "catalog");

    assertThat(run.exitCode()).as(run.err()).isZero();
  }

  @Test
  void anUnreadableEnvFallsThroughToLoopback(@TempDir Path config) throws Exception {
    Files.createDirectories(config.resolve("lvh-yard/.env"));

    Run run = yardProcess(Map.of("XDG_CONFIG_HOME", config.toString()), "catalog");

    // A dashboard of this computer's own may answer at the default; else the error names it.
    assertThat(run.exitCode() == 0 || run.err().contains("at http://127.0.0.1:7780"))
        .as(run.err())
        .isTrue();
  }

  @Test
  void withoutAnEnvTheDashboardIsOnLoopback() throws Exception {
    Run run = yardProcess(Map.of(), "catalog");

    // A dashboard of this computer's own may answer at the default; else the error names it.
    assertThat(run.exitCode() == 0 || run.err().contains("at http://127.0.0.1:7780"))
        .as(run.err())
        .isTrue();
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
  void initsNextStepsAndCatalogAgreeOnTheDashboardForABlankBindAddressAndPort(@TempDir Path config)
      throws Exception {
    Path folder = Files.createDirectories(config.resolve("lvh-yard"));
    Files.writeString(
        folder.resolve(".env"),
        "YARD_WORKSTATION=ws-a\nYARD_DASHBOARD_BIND_ADDRESS= \nYARD_DASHBOARD_PORT= \n");

    Run init = yardAsTyped("init", "--dir", folder.toString(), "--reinit");
    Run catalog = yardProcess(Map.of("XDG_CONFIG_HOME", config.toString()), "catalog");

    assertThat(init.out())
        .contains("Open the dashboard: http://127.0.0.1:7780\n")
        .contains("  4. Make sure that LVH Yard works: lvh-yard status\n\n");
    // A dashboard of this computer's own may answer at the default; else the error names it.
    assertThat(catalog.exitCode() == 0 || catalog.err().contains("at http://127.0.0.1:7780"))
        .as(catalog.err())
        .isTrue();
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
}
