package dev.palazon.lvhyard.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InitTest extends YardTestSupport {

  @Test
  void initWritesComposeFileAndEnvWithTheWorkstationIntoDir(@TempDir Path dir) throws Exception {
    Run run = yardAsTyped("init", "--dir", dir.toString(), "--workstation", "ws-a");

    assertThat(run.exitCode()).isZero();
    assertThat(dir.resolve("compose.yaml")).exists();
    assertThat(env(dir)).contains("YARD_WORKSTATION=ws-a");
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
  void initMakesAWorkstationNameFromAHostname() {
    assertThat(Init.nameFromHostname("My-Box.example.com")).contains("my-box");
    assertThat(Init.nameFromHostname("-dev_box-")).contains("dev-box");
    assertThat(Init.nameFromHostname("a".repeat(62) + "-b")).contains("a".repeat(62));
    assertThat(Init.nameFromHostname("")).isEmpty();
    assertThat(Init.nameFromHostname(null)).isEmpty();
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
    Files.writeString(backup, "version: 2\n");

    Run run = yardAsTyped("init", "--dir", dir.toString(), "--reinit");

    assertThat(run.exitCode()).as(run.err()).isZero();
    assertThat(backup).hasContent("version: 2");
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
}
