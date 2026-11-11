package cc.lvhy.yard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.yaml.snakeyaml.Yaml;

/**
 * Traefik's admin page and ping, with Traefik started exactly as compose.yaml's {@code traefik}
 * service says: its image, entrypoint and command, with Compose's variables filled in from a test
 * {@code .env}.
 */
class TraefikAdminIT {

  /** Three free ports, taken together: one by one, the system may give the same port twice. */
  private static final int[] PORTS = freePorts(3);

  private static final int HTTP_PORT = PORTS[0];
  private static final int ADMIN_PORT = PORTS[1];

  private static GenericContainer<?> traefik;

  private final HttpClient http = HttpClient.newHttpClient();

  @BeforeAll
  static void startTraefikFromCompose() throws IOException {
    traefik =
        composeService(
                "traefik",
                Map.of(
                    "YARD_HTTP_PORT", String.valueOf(HTTP_PORT),
                    "YARD_TRAEFIK_ADMIN_PORT", String.valueOf(ADMIN_PORT),
                    "YARD_DASHBOARD_PORT", String.valueOf(PORTS[2])))
            .waitingFor(Wait.forLogMessage(".*Starting provider \\*http\\.Provider.*", 1));
    traefik.start();
  }

  @AfterAll
  static void stopTraefik() {
    if (traefik != null) {
      traefik.stop();
    }
  }

  @Test
  void pingAnswersOnTheAdminPort() {
    HttpResponse<String> ping = getOnceRouted(ADMIN_PORT, "/ping");

    assertThat(ping.statusCode()).isEqualTo(200);
    assertThat(ping.body()).isEqualTo("OK");
  }

  @Test
  void adminPageAndItsApiAnswerOnTheAdminPort() {
    assertThat(getOnceRouted(ADMIN_PORT, "/dashboard/").statusCode()).isEqualTo(200);
    assertThat(getOnceRouted(ADMIN_PORT, "/api/overview").statusCode()).isEqualTo(200);
  }

  @Test
  void webEntrypointServesNeitherPingNorTheAdminPage() {
    // Once the admin entry point answers, Traefik has its routers for ping and the API.
    getOnceRouted(ADMIN_PORT, "/ping");

    assertThat(get(HTTP_PORT, "/ping").statusCode()).isEqualTo(404);
    assertThat(get(HTTP_PORT, "/dashboard/").statusCode()).isEqualTo(404);
    assertThat(get(HTTP_PORT, "/api/overview").statusCode()).isEqualTo(404);
  }

  /**
   * The answer to {@code path} once it is not a 404: Traefik adds its own routers for ping and the
   * API a moment after it logs that its providers start.
   */
  private HttpResponse<String> getOnceRouted(int port, String path) {
    return await()
        .atMost(Duration.ofSeconds(10))
        .until(() -> get(port, path), answer -> answer.statusCode() != 404);
  }

  private HttpResponse<String> get(int port, String path) {
    try {
      return http.send(
          HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).build(),
          HttpResponse.BodyHandlers.ofString());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  /**
   * compose.yaml's service {@code name} on the host's network, as Compose would run it with {@code
   * env} as its .env: unset variables take their defaults.
   */
  @SuppressWarnings("unchecked")
  private static GenericContainer<?> composeService(String name, Map<String, String> env)
      throws IOException {
    Map<String, Object> compose;
    try (InputStream file = Files.newInputStream(Path.of("..", "compose.yaml"))) {
      compose = new Yaml().load(file);
    }
    Map<String, Object> service =
        (Map<String, Object>) ((Map<String, Object>) compose.get("services")).get(name);
    GenericContainer<?> container =
        new GenericContainer<>(interpolate((String) service.get("image"), env))
            .withNetworkMode("host");
    ((Map<String, String>) service.getOrDefault("environment", Map.of()))
        .forEach((key, value) -> container.withEnv(key, interpolate(value, env)));
    List<String> entrypoint = (List<String>) service.get("entrypoint");
    List<String> command = (List<String>) service.get("command");
    return container.withCreateContainerCmdModifier(create ->
        create
            .withEntrypoint(entrypoint.stream().map(part -> interpolate(part, env)).toList())
            .withCmd(command.stream().map(part -> interpolate(part, env)).toList()));
  }

  private static final Pattern VARIABLE =
      Pattern.compile("\\$\\$|\\$\\{(\\w+)(?::?([-?])([^}]*))?}");

  /** Compose's interpolation: {@code ${NAME}}, {@code ${NAME:-default}}, and {@code $$} for $. */
  private static String interpolate(String text, Map<String, String> env) {
    Matcher matcher = VARIABLE.matcher(text);
    StringBuilder result = new StringBuilder();
    while (matcher.find()) {
      String value;
      if (matcher.group().equals("$$")) {
        value = "$";
      } else {
        value = env.get(matcher.group(1));
        if ((value == null || value.isEmpty()) && "-".equals(matcher.group(2))) {
          value = matcher.group(3);
        }
        if (value == null) {
          value = "";
        }
      }
      matcher.appendReplacement(result, Matcher.quoteReplacement(value));
    }
    matcher.appendTail(result);
    return result.toString();
  }

  private static int[] freePorts(int count) {
    List<ServerSocket> sockets = new ArrayList<>();
    try {
      for (int i = 0; i < count; i++) {
        sockets.add(new ServerSocket(0));
      }
      return sockets.stream().mapToInt(ServerSocket::getLocalPort).toArray();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } finally {
      for (ServerSocket socket : sockets) {
        try {
          socket.close();
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      }
    }
  }
}
