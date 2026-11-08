package dev.palazon.lvhyard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
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
 * a real Consul behind it, federated with a {@link RemoteWorkstation}'s. Both are shared by the
 * tests of one JVM, and put back as they were just after the first setup before each test.
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
      "lvh-yard.consul.unreachable-pause=2s",
      // A new Bound Instance passes its first check in about a second.
      "lvh-yard.bound-instances.check-interval=1s",
      // A new event stream gets its first line, and so its answer, within a second.
      "lvh-yard.events.heartbeat-interval=1s"
    })
@Import(TestcontainersConfiguration.class)
abstract class DashboardTest {

  static final String WORKSTATION = "ws-test";

  static final String BASE_DOMAIN = "lvh.test";

  private static final AtomicInteger SERVICE_NAMES = new AtomicInteger();

  /** Every port {@link #newLanPort} has given out, as the Consul container keeps its listeners. */
  private static final Set<Integer> LAN_PORTS = ConcurrentHashMap.newKeySet();

  /** This Workstation's Consul just after the first setup, which each test starts from. */
  private static volatile ConsulBaseline baseline;

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
    RestClient consul =
        RestClient.create(
            "http://"
                + consulContainer.getHost()
                + ":"
                + consulContainer.getMappedPort(TestcontainersConfiguration.CONSUL_HTTP_PORT));
    if (baseline == null) {
      // The dashboard does its first setup in the background, after it has started.
      RemoteWorkstation.awaitFirstSetup(consul);
      baseline = ConsulBaseline.of(consul);
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

  /**
   * The Service page's summary card {@code name}: {@code state}, {@code publication}, {@code
   * passing} or {@code routing}.
   */
  static String summaryCard(String page, String name) {
    return div(page, "data-summary=\"" + name + "\"");
  }

  /** The whole {@code form} element that posts to {@code action}. A form never holds another. */
  static String formTo(String page, String action) {
    int at = page.indexOf("action=\"" + action + "\"");
    if (at < 0) {
      throw new AssertionError("No form that posts to " + action);
    }
    int start = page.lastIndexOf("<form", at);
    return page.substring(start, page.indexOf("</form>", start) + "</form>".length());
  }

  /**
   * Checks that {@code page} is a create form page: a link with the title opens it from {@code
   * list}, it posts to {@code action}, and Cancel goes back to {@code list}.
   */
  void assertFormPage(String list, String path, String title, String action) {
    String listPage = getPage(list).getBody();
    assertThat(listPage).doesNotContain("<dialog");
    String link = openingTag(listPage, "href=\"" + path + "\"");
    assertThat(link).startsWith("<a");
    assertThat(listPage).contains(link + title + "</a>");

    ResponseEntity<String> response = getPage(path);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    String page = response.getBody();
    assertThat(page).contains("<h1>" + title + "</h1>").doesNotContain("<dialog");
    String form = formTo(page, action);
    assertThat(openingTag(form, "action=\"" + action + "\"")).contains("method=\"post\"");
    assertThat(form).contains("type=\"submit\"").doesNotContain("aria-invalid");
    String cancel = openingTag(form, "href=\"" + list + "\"");
    assertThat(cancel).startsWith("<a");
    assertThat(form).contains(cancel + "Cancel</a>");
  }

  ResponseEntity<JsonNode> get(String path) {
    return http.get().uri(path).retrieve().toEntity(JsonNode.class);
  }

  /** A badge as the pages write it, such as {@code badge("success", "passing")}. */
  static String badge(String variant, String text) {
    return "<span class=\"badge\" data-variant=\"" + variant + "\">" + text + "</span>";
  }

  ResponseEntity<String> getPage(String path) {
    return http.get().uri(path).retrieve().toEntity(String.class);
  }

  /** As a client that accepts only {@code accept}, such as a browser with text/html. */
  ResponseEntity<String> getAccepting(String path, MediaType accept) {
    return http.get().uri(path).accept(accept).retrieve().toEntity(String.class);
  }

  /** As a client that asks for YAML, such as {@code lvh-yard export}. */
  ResponseEntity<String> getYaml(String path) {
    return http.get()
        .uri(path)
        .accept(MediaType.parseMediaType("application/yaml"), MediaType.APPLICATION_PROBLEM_JSON)
        .retrieve()
        .toEntity(String.class);
  }

  /** As a client that sends YAML, such as {@code lvh-yard import}. */
  ResponseEntity<JsonNode> postYaml(String path, String body) {
    return http.post()
        .uri(path)
        .contentType(MediaType.parseMediaType("application/yaml"))
        .accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON)
        .body(body)
        .retrieve()
        .toEntity(JsonNode.class);
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
   * A form with one file, as a browser uploads it. Redirects are not followed, so the answer to the
   * post itself is seen.
   */
  ResponseEntity<String> postFile(String path, String field, String filename, String text) {
    MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
    form.add(
        field,
        new ByteArrayResource(text.getBytes(StandardCharsets.UTF_8)) {
          @Override
          public String getFilename() {
            return filename;
          }
        });
    return http.post()
        .uri(path)
        .contentType(MediaType.MULTIPART_FORM_DATA)
        .body(form)
        .retrieve()
        .toEntity(String.class);
  }

  /**
   * Registers a Static Instance of {@code service} on this Workstation through the REST API, and
   * checks it took. A new Service stays Unpublished unless {@code instance} has {@code publish}, so
   * a test publishes it only when it needs to.
   */
  JsonNode register(String service, Map<String, Object> instance) {
    Map<String, Object> body = new HashMap<>(instance);
    body.putIfAbsent("publish", false);
    ResponseEntity<JsonNode> response = post(instancesPath(service), body);
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
   * API. A new Service stays Unpublished, as with {@link #register}.
   */
  JsonNode bind(String service, String container, int port) {
    ResponseEntity<JsonNode> response =
        post(
            containerBindingsPath(service),
            Map.of("container", container, "port", port, "publish", false));
    if (!response.getStatusCode().is2xxSuccessful()) {
      throw new AssertionError("Binding " + service + " answered " + response);
    }
    return response.getBody();
  }

  /**
   * Binds {@code service} on this Workstation to {@code composeService} of {@code project}'s {@code
   * port}. A new Service stays Unpublished, as with {@link #register}.
   */
  JsonNode bindCompose(String service, String project, String composeService, int port) {
    ResponseEntity<JsonNode> response =
        post(
            containerBindingsPath(service),
            Map.of(
                "composeProject",
                project,
                "composeService",
                composeService,
                "port",
                port,
                "publish",
                false));
    if (!response.getStatusCode().is2xxSuccessful()) {
      throw new AssertionError("Binding " + service + " answered " + response);
    }
    return response.getBody();
  }

  static final String ROUTE_PROFILES_PATH = "/api/workstation/route-profiles";

  /**
   * Adds or replaces Route Profiles of this Workstation through the REST API, and checks it took.
   */
  void putRouteProfiles(Object profiles) {
    ResponseEntity<JsonNode> response = put(ROUTE_PROFILES_PATH, profiles);
    if (!response.getStatusCode().is2xxSuccessful()) {
      throw new AssertionError("Adding Route Profiles answered " + response);
    }
  }

  /**
   * Removes every Route Profile of this Workstation straight from Consul, as the API refuses to
   * remove one that a Publication of an earlier test names.
   */
  void removeAllRouteProfiles() throws IOException, InterruptedException {
    consulContainer.execInContainer(
        "consul", "kv", "delete", "-recurse", "lvh-yard/route-profiles/");
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

  static class EventStreamClient implements AutoCloseable {
    final HttpResponse<InputStream> response;
    final BlockingQueue<String> lines = new LinkedBlockingQueue<>();
    final InputStream stream;
    final Thread readerThread;

    EventStreamClient(int port) throws Exception {
      HttpClient client = HttpClient.newHttpClient();
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create("http://127.0.0.1:" + port + "/events"))
              .header("Accept", "text/event-stream")
              .GET()
              .build();
      this.response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
      this.stream = response.body();
      this.readerThread =
          Thread.ofVirtual()
              .name("test-sse-reader")
              .start(() -> {
                try (BufferedReader reader =
                    new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                  String line;
                  while ((line = reader.readLine()) != null) {
                    lines.add(line);
                  }
                } catch (Exception ignored) {
                }
              });
    }

    @Override
    public void close() throws Exception {
      stream.close();
      readerThread.interrupt();
    }
  }
}
