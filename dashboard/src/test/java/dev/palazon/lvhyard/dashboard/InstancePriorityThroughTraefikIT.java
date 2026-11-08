package dev.palazon.lvhyard.dashboard;

import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;

/** The Instance Priority end to end, through a real Traefik that reads the dashboard's Routes. */
class InstancePriorityThroughTraefikIT extends InstancePriorityTestSupportIT {

  private static GenericContainer<?> traefik;

  @BeforeEach
  void startTraefik() {
    if (traefik == null) {
      traefik = TestcontainersConfiguration.traefik(port);
      traefik.start();
    }
  }

  @AfterAll
  static void stopTraefik() {
    if (traefik != null) {
      traefik.stop();
      traefik = null;
    }
  }

  @Test
  void hostnameReachesTheBoundInstanceWhileTheStaticInstanceIsStopped() {
    String web = newServiceName("web");
    int listening = newLanPort();
    answerHttpOnLanAddress(listening, "200 OK", "terminal");
    register(web, Map.of("port", listening, "check", Map.of("interval", "1s")));
    String name = newContainerName();
    startYardNetworkContainer(name, 80, "docker");
    bind(web, name, 80);
    publish(web);
    awaitOnlyAnswer(web, "terminal", "docker");

    stopListeningOnLanAddress(listening);

    awaitOnlyAnswer(web, "docker", "terminal");

    answerHttpOnLanAddress(listening, "200 OK", "terminal");

    awaitOnlyAnswer(web, "terminal", "docker");
  }

  /**
   * Waits until six requests in a row for {@code service}'s hostname get {@code body} at least once
   * and {@code other} never. Traefik takes turns between the Instances of a Route, so six requests
   * reach both while both are routed. A request may still fail, as the test listeners take one
   * connection at a time.
   */
  private static void awaitOnlyAnswer(String service, String body, String other) {
    String hostname = service + ".lvh.test";
    await()
        .atMost(SWITCH_TIMEOUT)
        .until(() -> {
          List<String> answers = new ArrayList<>();
          for (int request = 0; request < 6; request++) {
            answers.add(answerThroughTraefik(hostname));
          }
          return answers.stream().anyMatch(answer -> answer.endsWith("\r\n\r\n" + body))
              && answers.stream().noneMatch(answer -> answer.endsWith("\r\n\r\n" + other));
        });
  }

  /**
   * The whole answer, or nothing when the connection fails. By hand, as the JDK's HTTP client does
   * not let a request set its own Host header.
   */
  private static String answerThroughTraefik(String hostname) {
    try (Socket socket = new Socket()) {
      socket.connect(
          new InetSocketAddress("127.0.0.1", TestcontainersConfiguration.TRAEFIK_PORT), 2000);
      socket.setSoTimeout(5000);
      OutputStream out = socket.getOutputStream();
      out.write(
          ("GET / HTTP/1.1\r\nHost: " + hostname + "\r\nConnection: close\r\n\r\n")
              .getBytes(StandardCharsets.US_ASCII));
      out.flush();
      InputStream in = socket.getInputStream();
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      return "";
    }
  }
}
