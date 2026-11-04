package dev.palazon.lvhyard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;

/** A basic-auth Route Profile end to end, through a real Traefik that reads the dashboard. */
class RouteProfilesThroughTraefikTest extends DashboardTest {

  private static GenericContainer<?> traefik;

  @BeforeEach
  void startTraefik() {
    if (traefik == null) {
      traefik = TestcontainersConfiguration.traefik(port);
      traefik.start();
    }
  }

  @AfterEach
  void removeTheFile() throws IOException {
    Files.deleteIfExists(TestcontainersConfiguration.ROUTE_PROFILES);
  }

  @AfterAll
  static void stopTraefik() {
    if (traefik != null) {
      traefik.stop();
      traefik = null;
    }
  }

  @Test
  void basicAuthProfileAsksForCredentialsAndLetsTheUserIn() throws IOException {
    // The password is "secret".
    Files.writeString(
        TestcontainersConfiguration.ROUTE_PROFILES,
        """
        http:
          middlewares:
            team-login:
              basicAuth:
                users:
                  - "ana:$apr1$lvhyard1$p9S4tUHNaQRDYDEMe9KFK."
        """);
    await()
        .atMost(Duration.ofSeconds(10))
        .until(() -> get("/api/workstation/route-profiles").getBody().get("profiles").size() == 1);
    String web = newServiceName("web");
    int listening = newLanPort();
    answerHttpOnLanAddress(listening, "200 OK", "inside");
    register(web, Map.of("port", listening));
    put(publicationPath(web), Map.of("profiles", List.of("team-login")));
    String hostname = web + ".lvh.test";

    await()
        .atMost(Duration.ofSeconds(20))
        .until(() -> answerThroughTraefik(hostname, null).startsWith("HTTP/1.1 401"));
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() ->
            assertThat(answerThroughTraefik(hostname, "Basic YW5hOnNlY3JldA=="))
                .startsWith("HTTP/1.1 200")
                .endsWith("\r\n\r\ninside"));
    assertThat(answerThroughTraefik(hostname, "Basic YW5hOndyb25n")).startsWith("HTTP/1.1 401");
  }

  /**
   * The whole answer, or nothing when the connection fails. By hand, as the JDK's HTTP client does
   * not let a request set its own Host header.
   *
   * @param authorization the Authorization header's value, or {@code null} for none
   */
  private static String answerThroughTraefik(String hostname, String authorization) {
    try (Socket socket = new Socket()) {
      socket.connect(
          new InetSocketAddress("127.0.0.1", TestcontainersConfiguration.TRAEFIK_PORT), 2000);
      socket.setSoTimeout(5000);
      OutputStream out = socket.getOutputStream();
      out.write(
          ("GET / HTTP/1.1\r\nHost: "
                  + hostname
                  + (authorization == null ? "" : "\r\nAuthorization: " + authorization)
                  + "\r\nConnection: close\r\n\r\n")
              .getBytes(StandardCharsets.US_ASCII));
      out.flush();
      InputStream in = socket.getInputStream();
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      return "";
    }
  }
}
