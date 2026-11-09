package dev.palazon.lvhyard.dashboard;

import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The Instance Priority through a real Traefik. InstancePriorityComponentTest covers the rest, and
 * RoutingTest covers each case of the Route choice itself.
 */
class InstancePriorityIT extends DashboardIT {

  /** Long enough for a check to fail and for Traefik to fetch the new Route. */
  static final Duration SWITCH_TIMEOUT = Duration.ofSeconds(30);

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

  @Test
  void hostnameReachesTheSecondStaticInstanceWhileTheFirstIsStopped() {
    start(TestcontainersConfiguration.traefik(port));
    String web = newServiceName("web");
    int first = newLanPort();
    int second = newLanPort();
    answerHttpOnLanAddress(first, "200 OK", "first");
    answerHttpOnLanAddress(second, "200 OK", "second");
    register(web, Map.of("port", first, "check", Map.of("interval", "1s")));
    register(web, Map.of("port", second, "check", Map.of("interval", "1s")));
    publish(web);
    awaitOnlyAnswer(web, "first", "second");

    stopListeningOnLanAddress(first);

    awaitOnlyAnswer(web, "second", "first");

    answerHttpOnLanAddress(first, "200 OK", "first");

    awaitOnlyAnswer(web, "first", "second");
  }
}
