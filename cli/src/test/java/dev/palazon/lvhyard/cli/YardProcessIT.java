package dev.palazon.lvhyard.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

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
/**
 * The CLI tests that need a real process: lvh-yard reads the real environment, and docker compose
 * reads the folder that init writes.
 */
class YardProcessIT extends YardTestSupport {

  @Test
  void dashboardUrlComesFromTheEnvironment() throws Exception {
    dashboard.answer(
        "GET",
        "/api/yard",
        200,
        """
        {"home": {"name": "ws-a", "services": []}, "remotes": []}
        """);

    Run run = realProcess(Map.of("YARD_DASHBOARD_URL", dashboard.url().toString()), "catalog");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out()).isEqualTo("ws-a (this Workstation): no Services\n");
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
