package dev.palazon.lvhyard.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The {@code lvh-yard} command as a shell runs it: arguments in, exit code and output out. Behind
 * it is a stub dashboard that answers only what {@code openapi.yaml} describes.
 */
class YardTest extends YardTestSupport {

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

    Run run = yardAsTyped("--dashboard", nothingListens, "catalog");

    assertThat(run.exitCode()).isNotZero();
    assertThat(run.err())
        .contains("The dashboard does not answer at")
        .contains(nothingListens)
        .contains("docker compose up -d");
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
          yardAsTyped("--dashboard", "http://127.0.0.1:" + other.getAddress().getPort(), "catalog");

      assertThat(run.exitCode()).isNotZero();
      assertThat(run.err()).contains("is not the LVH Yard dashboard");
    } finally {
      other.stop(0);
    }
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
}
