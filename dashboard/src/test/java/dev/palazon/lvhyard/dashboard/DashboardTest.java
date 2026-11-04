package dev.palazon.lvhyard.dashboard;

import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import tools.jackson.databind.JsonNode;

/**
 * The single test seam: the running dashboard as a black box, on Workstation {@code ws-test}, with
 * a real Consul behind it, federated with a {@link RemoteWorkstation}'s. Both are shared by every
 * test, so each test registers Services under names of its own ({@link #newServiceName}).
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "lvh-yard.workstation.name=" + DashboardTest.WORKSTATION,
      "lvh-yard.workstation.base-domain=" + DashboardTest.BASE_DOMAIN,
      // The test Workstation joins no Peer: ws-remote joins it. Not the Peers of a developer's
      // .env.
      "lvh-yard.consul.peers=",
      // A paused ws-remote is found unreachable, and back, in seconds.
      "lvh-yard.consul.answer-timeout=1s",
      "lvh-yard.consul.unreachable-pause=2s"
    })
@Import(TestcontainersConfiguration.class)
abstract class DashboardTest {

  static final String WORKSTATION = "ws-test";

  static final String BASE_DOMAIN = "lvh.test";

  private static final AtomicInteger SERVICE_NAMES = new AtomicInteger();

  /** Every port {@link #newLanPort} has given out, as the Consul container keeps its listeners. */
  private static final Set<Integer> LAN_PORTS = ConcurrentHashMap.newKeySet();

  /** Whether this Workstation's dashboard has kept its Core Services since the tests started. */
  private static volatile boolean coreServicesKept;

  @LocalServerPort int port;

  @Autowired GenericContainer<?> consulContainer;

  /** The test Yard's other Workstation, whose Services are Remote Services here. */
  @Autowired RemoteWorkstation remote;

  @Autowired Network yardNetwork;

  private RestClient http;

  /** The containers this test started, removed after it. */
  private final List<GenericContainer<?>> containers = new ArrayList<>();

  @BeforeEach
  void connect() {
    http =
        RestClient.builder()
            .baseUrl("http://127.0.0.1:" + port)
            .defaultStatusHandler(status -> true, (request, response) -> {})
            .build();
    if (!coreServicesKept) {
      // The dashboard keeps its Core Services in the background, after it has started.
      RemoteWorkstation.awaitCoreServicesKept(
          RestClient.create(
              "http://"
                  + consulContainer.getHost()
                  + ":"
                  + consulContainer.getMappedPort(TestcontainersConfiguration.CONSUL_HTTP_PORT)),
          Instant.EPOCH);
      coreServicesKept = true;
    }
  }

  @AfterEach
  void removeContainers() {
    containers.forEach(GenericContainer::stop);
  }

  /** A container name no other test, nor an earlier run, uses. */
  static String newContainerName() {
    return "yard-test-" + UUID.randomUUID().toString().substring(0, 8);
  }

  /**
   * Starts a container called {@code name} on Docker's default bridge network that accepts TCP
   * connections on {@code containerPort}, published on a free host port when {@code publish} is
   * true. It is removed after the test.
   */
  GenericContainer<?> startContainer(String name, int containerPort, boolean publish) {
    GenericContainer<?> container = TestcontainersConfiguration.listener(name, containerPort);
    if (publish) {
      container.withExposedPorts(containerPort);
    }
    containers.add(container);
    container.start();
    return container;
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

  /**
   * Starts a container called {@code name} on the host's network that accepts TCP connections on a
   * free port, and answers that port. It is removed after the test.
   */
  int startHostNetworkContainer(String name) throws IOException {
    int port;
    try (ServerSocket free = new ServerSocket(0)) {
      port = free.getLocalPort();
    }
    GenericContainer<?> container =
        TestcontainersConfiguration.listener(name, port).withNetworkMode("host");
    containers.add(container);
    container.start();
    return port;
  }

  /** A Compose project name no other test, nor an earlier run, uses. */
  static String newComposeProject() {
    return "yard-test-" + UUID.randomUUID().toString().substring(0, 8);
  }

  /**
   * Starts a container called {@code name} with the labels Compose gives a container of {@code
   * composeService} in {@code project}. Otherwise it is as {@link #startContainer} starts one, with
   * {@code containerPort} unpublished.
   */
  GenericContainer<?> startComposeContainer(
      String name, String project, String composeService, int containerPort) {
    GenericContainer<?> container =
        TestcontainersConfiguration.listener(name, containerPort)
            .withLabel("com.docker.compose.project", project)
            .withLabel("com.docker.compose.service", composeService);
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

  /** A Service name no other test uses, such as {@code grafana-3}. */
  static String newServiceName(String prefix) {
    return prefix + "-" + SERVICE_NAMES.incrementAndGet();
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
   * A port that no test in this JVM has had yet, for a listener at the {@link #lanAddress}. A port
   * free on this computer can come back from two calls in a row, and the same host and port
   * registers the same Instance again, so a test would get one Instance where it made two.
   */
  static int newLanPort() {
    while (true) {
      int port = TestcontainersConfiguration.freePorts(1)[0];
      if (LAN_PORTS.add(port)) {
        return port;
      }
    }
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

  /** The opening tag of the element with {@code attribute}. */
  static String openingTag(String page, String attribute) {
    int at = page.indexOf(attribute);
    if (at < 0) {
      throw new AssertionError("No element with " + attribute);
    }
    return page.substring(page.lastIndexOf("<", at), page.indexOf(">", at) + 1);
  }

  /** The whole {@code div} element with {@code attribute}, up to its own closing tag. */
  static String div(String page, String attribute) {
    int start = page.lastIndexOf("<div", page.indexOf(attribute));
    int depth = 0;
    int at = start;
    do {
      int open = page.indexOf("<div", at + 1);
      int close = page.indexOf("</div>", at + 1);
      depth += open >= 0 && open < close ? 1 : -1;
      at = open >= 0 && open < close ? open : close;
    } while (depth >= 0);
    return page.substring(start, at + "</div>".length());
  }

  ResponseEntity<JsonNode> get(String path) {
    return http.get().uri(path).retrieve().toEntity(JsonNode.class);
  }

  ResponseEntity<String> getPage(String path) {
    return http.get().uri(path).retrieve().toEntity(String.class);
  }

  ResponseEntity<JsonNode> post(String path, Object body) {
    return http.post()
        .uri(path)
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .toEntity(JsonNode.class);
  }

  ResponseEntity<JsonNode> put(String path) {
    return http.put().uri(path).retrieve().toEntity(JsonNode.class);
  }

  ResponseEntity<JsonNode> put(String path, Object body) {
    return http.put()
        .uri(path)
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .toEntity(JsonNode.class);
  }

  ResponseEntity<JsonNode> delete(String path) {
    return http.delete().uri(path).retrieve().toEntity(JsonNode.class);
  }

  /**
   * A form submitted by a browser. Redirects are not followed, so the answer to the post itself is
   * seen.
   */
  ResponseEntity<String> postForm(String path, Map<String, String> fields) {
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    fields.forEach(form::add);
    return http.post()
        .uri(path)
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .body(form)
        .retrieve()
        .toEntity(String.class);
  }

  /**
   * Registers a Static Instance of {@code service} on this Workstation through the REST API, and
   * checks it took.
   */
  JsonNode register(String service, Map<String, Object> instance) {
    ResponseEntity<JsonNode> response = post(instancesPath(service), instance);
    if (!response.getStatusCode().is2xxSuccessful()) {
      throw new AssertionError("Registering " + service + " answered " + response);
    }
    return response.getBody();
  }

  /**
   * One of this Workstation's own Services, which the REST API reads and changes without naming the
   * Workstation.
   */
  static String servicePath(String service) {
    return "/api/workstation/services/" + service;
  }

  static String instancesPath(String service) {
    return servicePath(service) + "/instances";
  }

  static String containerBindingsPath(String service) {
    return servicePath(service) + "/container-bindings";
  }

  /**
   * Binds {@code service} on this Workstation to {@code container}'s {@code port} through the REST
   * API.
   */
  JsonNode bind(String service, String container, int port) {
    ResponseEntity<JsonNode> response =
        post(containerBindingsPath(service), Map.of("container", container, "port", port));
    if (!response.getStatusCode().is2xxSuccessful()) {
      throw new AssertionError("Binding " + service + " answered " + response);
    }
    return response.getBody();
  }

  /**
   * Binds {@code service} on this Workstation to {@code composeService} of {@code project}'s {@code
   * port}.
   */
  JsonNode bindCompose(String service, String project, String composeService, int port) {
    ResponseEntity<JsonNode> response =
        post(
            containerBindingsPath(service),
            Map.of("composeProject", project, "composeService", composeService, "port", port));
    if (!response.getStatusCode().is2xxSuccessful()) {
      throw new AssertionError("Binding " + service + " answered " + response);
    }
    return response.getBody();
  }

  static String publicationPath(String service) {
    return servicePath(service) + "/publication";
  }

  /** Publishes {@code service} through the REST API, and checks it took. */
  void publish(String service) {
    ResponseEntity<JsonNode> response = put(publicationPath(service));
    if (!response.getStatusCode().is2xxSuccessful()) {
      throw new AssertionError("Publishing " + service + " answered " + response);
    }
  }
}
