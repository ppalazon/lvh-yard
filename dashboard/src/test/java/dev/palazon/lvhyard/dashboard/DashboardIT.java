package dev.palazon.lvhyard.dashboard;

import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.web.client.RestClient;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import tools.jackson.databind.JsonNode;

/**
 * The dashboard with the real test Yard behind it: a real Consul, federated with a {@link
 * RemoteWorkstation}'s. Both are shared by the tests of one JVM, and put back as they were just
 * after the first setup before each test.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "lvh-yard.workstation.name=" + DashboardClient.WORKSTATION,
      "lvh-yard.workstation.base-domain=" + DashboardClient.BASE_DOMAIN,
      // The test Workstation joins no Peer: ws-remote joins it. Not the Peers of a developer's
      // .env.
      "lvh-yard.consul.peers=",
      // A paused ws-remote is found unreachable, and back, in seconds.
      "lvh-yard.consul.answer-timeout=1s",
      "lvh-yard.consul.unreachable-pause=2s",
      // A new Bound Instance passes its first check in about a second.
      "lvh-yard.bound-instances.check-interval=1s",
      // A new event stream gets its first line, and so its answer, within a second.
      "lvh-yard.events.heartbeat-interval=1s"
    })
@Import(TestcontainersConfiguration.class)
abstract class DashboardIT extends DashboardClient {

  /**
   * Each Consul just after its first setup, which each test starts from, by container ID. A JVM can
   * hold two test Yards: one for fake Docker and one for {@link RealDockerIT}.
   */
  private static final Map<String, ConsulBaseline> BASELINES = new ConcurrentHashMap<>();

  @Autowired GenericContainer<?> consulContainer;

  /** The test Yard's other Workstation, whose Services are Remote Services here. */
  @Autowired RemoteWorkstation remote;

  @Autowired Network yardNetwork;

  /** The containers this test started, removed after it. */
  private final List<GenericContainer<?>> containers = new ArrayList<>();

  @BeforeEach
  void connect() {
    RestClient consul =
        RestClient.create(
            "http://"
                + consulContainer.getHost()
                + ":"
                + consulContainer.getMappedPort(TestcontainersConfiguration.CONSUL_HTTP_PORT));
    ConsulBaseline baseline =
        BASELINES.computeIfAbsent(
            consulContainer.getContainerId(),
            id -> {
              // The dashboard does its first setup in the background, after it has started.
              RemoteWorkstation.awaitFirstSetup(consul);
              return ConsulBaseline.of(consul);
            });
    if (docker != null) {
      docker.reset();
    }
    baseline.restore(consul);
    TestcontainersConfiguration.empty(TestcontainersConfiguration.BACKUPS);
    if (remote.reset()) {
      awaitRemoteAnswers(consul);
    }
  }

  /**
   * Waits until Consul finds ws-remote alive again and this dashboard reads it again, after a test
   * paused it. Otherwise the next test sees a failed Peer or an unreachable Workstation.
   */
  private void awaitRemoteAnswers(RestClient consul) {
    await()
        .atMost(Duration.ofSeconds(60))
        .ignoreExceptions()
        .until(() ->
            consul
                .get()
                .uri("/v1/agent/members?wan=1")
                .retrieve()
                .body(JsonNode.class)
                .valueStream()
                .allMatch(member -> member.get("Status").asInt() == 1));
    await()
        .atMost(Duration.ofSeconds(60))
        .until(() ->
            get("/api/yard")
                .getBody()
                .get("remotes")
                .valueStream()
                .noneMatch(workstation -> workstation.get("unreachable").asBoolean()));
  }

  @AfterEach
  void removeContainers() {
    containers.forEach(GenericContainer::stop);
  }

  /**
   * Starts a container called {@code name} on the test Yard's network, where the Consul checks
   * reach it, that accepts TCP connections on {@code containerPort}. It is removed after the test.
   */
  GenericContainer<?> startYardNetworkContainer(String name, int containerPort) {
    return startYardNetworkContainer(
        TestcontainersConfiguration.listener(name, containerPort).withNetwork(yardNetwork));
  }

  /**
   * As {@link #startYardNetworkContainer(String, int)}, but it answers every HTTP request on {@code
   * containerPort} with 200 and the text {@code body}.
   */
  GenericContainer<?> startYardNetworkContainer(String name, int containerPort, String body) {
    return startYardNetworkContainer(
        TestcontainersConfiguration.httpListener(name, containerPort, body)
            .withNetwork(yardNetwork));
  }

  private GenericContainer<?> startYardNetworkContainer(GenericContainer<?> container) {
    return start(container);
  }

  /** Starts {@code container}, which is removed after the test. */
  GenericContainer<?> start(GenericContainer<?> container) {
    containers.add(container);
    container.start();
    return container;
  }

  /** The address {@code container} has on its network. */
  static String containerAddress(GenericContainer<?> container) {
    return container
        .getContainerInfo()
        .getNetworkSettings()
        .getNetworks()
        .values()
        .iterator()
        .next()
        .getIpAddress();
  }

  /**
   * Stops {@code container} as {@code docker stop} does: it stays, stopped, until it is removed.
   */
  static void stopContainer(GenericContainer<?> container) {
    DockerClientFactory.instance()
        .client()
        .stopContainerCmd(container.getContainerId())
        .withTimeout(1)
        .exec();
  }

  /** The test Workstation's LAN address: the address its Consul binds to. */
  String lanAddress() {
    return consulContainer
        .getContainerInfo()
        .getNetworkSettings()
        .getNetworks()
        .values()
        .iterator()
        .next()
        .getIpAddress();
  }

  /**
   * Starts a process that accepts TCP connections on {@code port} at the {@link #lanAddress}, as a
   * Service on the Workstation would, and waits until it does. It runs until the Consul container
   * stops. Its {@code nc} ends after each connection, such as a health check's, so a loop starts it
   * again.
   */
  void listenOnLanAddress(int port) {
    try {
      consulContainer.execInContainer(
          "sh", "-c", "while true; do nc -l -p " + port + " >/dev/null; done >/dev/null 2>&1 &");
    } catch (IOException | InterruptedException e) {
      throw new IllegalStateException("Could not listen on port " + port, e);
    }
    awaitAccepting(port);
  }

  /**
   * Fails at once when nothing accepts, rather than leaving a test to wait for a check that never
   * passes.
   */
  private void awaitAccepting(int port) {
    await()
        .atMost(Duration.ofSeconds(10))
        .ignoreExceptions()
        .alias("a listener on " + lanAddress() + ":" + port)
        .until(() -> {
          try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(lanAddress(), port), 1000);
            return true;
          }
        });
  }

  /**
   * Stops what {@link #listenOnLanAddress} or {@link #answerHttpOnLanAddress} started on {@code
   * port}. The loop goes first, so it starts no new {@code nc}.
   */
  void stopListeningOnLanAddress(int port) {
    try {
      consulContainer.execInContainer("pkill", "-f", "while true.* nc -l -p " + port + " ");
      consulContainer.execInContainer("pkill", "-f", "^nc -l -p " + port + "$");
    } catch (IOException | InterruptedException e) {
      throw new IllegalStateException("Could not stop listening on port " + port, e);
    }
  }

  /**
   * Starts a process that answers every HTTP request on {@code port} at the {@link #lanAddress}
   * with {@code status}, such as {@code 503 Service Unavailable}, and the text {@code body}. It
   * runs until the Consul container stops.
   */
  void answerHttpOnLanAddress(int port, String status, String body) {
    String response =
        "HTTP/1.1 "
            + status
            + "\\r\\nContent-Type: text/plain\\r\\nContent-Length: "
            + body.length()
            + "\\r\\nConnection: close\\r\\n\\r\\n"
            + body;
    try {
      consulContainer.execInContainer(
          "sh",
          "-c",
          "while true; do printf '"
              + response
              + "' | nc -l -p "
              + port
              + " >/dev/null; done"
              + " >/dev/null 2>&1 &");
    } catch (IOException | InterruptedException e) {
      throw new IllegalStateException("Could not answer HTTP on port " + port, e);
    }
    awaitAccepting(port);
  }

  /**
   * Removes every Route Profile of this Workstation straight from Consul, as the API refuses to
   * remove one that a Publication of an earlier test names.
   */
  @Override
  void removeAllRouteProfiles() throws IOException, InterruptedException {
    consulContainer.execInContainer(
        "consul", "kv", "delete", "-recurse", "lvh-yard/route-profiles/");
  }
}
