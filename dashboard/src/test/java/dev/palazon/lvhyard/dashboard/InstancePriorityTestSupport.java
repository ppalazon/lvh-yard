package dev.palazon.lvhyard.dashboard;

import static dev.palazon.lvhyard.dashboard.TraefikConfigurationTest.serverUrlsAt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.testcontainers.containers.GenericContainer;
import tools.jackson.databind.JsonNode;

/**
 * Static and Bound Instances whose checks pass, for the Instance Priority tests. They are split in
 * classes that run side by side, as each Bound Instance waits up to 10 s for its first check.
 */
abstract class InstancePriorityTestSupport extends DashboardTest {

  /**
   * Long enough for a Bound Instance to appear and for its first check, every 10 s by default, to
   * run.
   */
  static final Duration SWITCH_TIMEOUT = Duration.ofSeconds(30);

  /**
   * Registers a Static Instance of {@code service} that listens on the LAN address, with a check
   * every second, and answers its address.
   */
  String staticInstance(String service) {
    int port = newLanPort();
    listenOnLanAddress(port);
    register(service, Map.of("port", port, "check", Map.of("interval", "1s")));
    return lanAddress() + ":" + port;
  }

  /**
   * Binds {@code service} to a new container that listens, waits until its Bound Instance passes,
   * and answers its address. So a test never sees the Route before the Bound Instance is there.
   */
  String boundInstance(String service) {
    String name = newContainerName();
    GenericContainer<?> container = startYardNetworkContainer(name, 80);
    bind(service, name, 80);
    await()
        .atMost(SWITCH_TIMEOUT)
        .until(() ->
            get(servicePath(service))
                .getBody()
                .get("instances")
                .valueStream()
                .anyMatch(instance ->
                    instance.has("containerBinding")
                        && instance.get("health").asString().equals("passing")));
    return containerAddress(container) + ":80";
  }

  /** Waits until this Workstation routes {@code service} to exactly {@code addresses}. */
  void awaitRoutedTo(String service, String... addresses) {
    awaitRoutedTo(
        () -> get("/traefik/configuration").getBody().get("http"),
        service + ".lvh.test",
        addresses);
  }

  /** Waits until {@code configuration} routes {@code hostname} to exactly {@code addresses}. */
  static void awaitRoutedTo(
      Supplier<JsonNode> configuration, String hostname, String... addresses) {
    await()
        .atMost(SWITCH_TIMEOUT)
        .untilAsserted(() ->
            assertThat(serverUrlsAt(configuration.get(), hostname))
                .containsExactlyInAnyOrder(
                    Arrays.stream(addresses)
                        .map(address -> "http://" + address)
                        .toArray(String[]::new)));
  }

  /** The port of an address that {@link #staticInstance} answers. */
  static int port(String address) {
    return Integer.parseInt(address.substring(address.lastIndexOf(':') + 1));
  }

  static List<String> texts(JsonNode array) {
    return array.valueStream().map(JsonNode::asString).toList();
  }
}
