package dev.palazon.lvhyard.dashboard;

import static org.awaitility.Awaitility.await;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import tools.jackson.databind.JsonNode;

/**
 * The test Yard's other Workstation, {@value #NAME}: its own Consul datacenter, joined to the test
 * Workstation's by WAN federation, and its own dashboard, which the tests change it through as its
 * developer would.
 */
final class RemoteWorkstation implements AutoCloseable {

  static final String NAME = "ws-remote";

  /** Where a dashboard writes when it has kept its Core Services after it started. */
  static final String CORE_SERVICES_INITIALIZED = "lvh-yard/core-services/initialized";

  /**
   * This Workstation's {@code route-profiles.yaml}, in a folder of this JVM's own, so its dashboard
   * never reads a developer's own file. Tests write it; it does not exist at first.
   */
  static final Path ROUTE_PROFILES = TestcontainersConfiguration.routeProfilesPath();

  /** How long a dashboard may take to keep its Core Services after it started. */
  private static final Duration CORE_SERVICES_WAIT = Duration.ofSeconds(30);

  private final GenericContainer<?> consul;

  private ConfigurableApplicationContext dashboard;

  private RestClient http;

  /** Whether this Workstation's Consul answers, as {@link #stopAnswering} leaves it. */
  private boolean answering = true;

  /**
   * Starts the Workstation's Consul, joined to the Consul at {@code peer} on {@code network}, and
   * its dashboard.
   */
  RemoteWorkstation(Network network, String peer) {
    consul =
        TestcontainersConfiguration.consul()
            .withNetwork(network)
            .withCommand(
                TestcontainersConfiguration.consulCommand(NAME, "-retry-join-wan=" + peer));
    consul.start();
    Instant starting = Instant.now();
    startDashboard();
    awaitCoreServicesKept(consulHttp(), starting);
  }

  /**
   * Stops this Workstation's dashboard, does {@code meanwhile}, and starts the dashboard again.
   * When its Consul answers, it waits until the dashboard has kept its Core Services.
   */
  void whileDashboardIsStopped(Runnable meanwhile) {
    whileDashboardIsStopped(meanwhile, new String[0]);
  }

  /**
   * Stops this Workstation's dashboard, does {@code meanwhile}, and starts the dashboard again with
   * {@code settings} too, such as {@code --server.address=0.0.0.0}, which win over the usual ones.
   * When its Consul answers, it waits until the dashboard has kept its Core Services.
   */
  void whileDashboardIsStopped(Runnable meanwhile, String... settings) {
    dashboard.close();
    try {
      meanwhile.run();
    } finally {
      Instant starting = Instant.now();
      startDashboard(settings);
      if (answering) {
        awaitCoreServicesKept(consulHttp(), starting);
      }
    }
  }

  /**
   * Starts this Workstation's dashboard again with {@code settings} too, such as {@code
   * --lvh-yard.docker.host=unix:///missing.sock}, does {@code meanwhile}, and starts it again with
   * the usual settings.
   */
  void whileDashboardRunsWith(Runnable meanwhile, String... settings) {
    whileDashboardIsStopped(() -> {}, settings);
    try {
      meanwhile.run();
    } finally {
      whileDashboardIsStopped(() -> {});
    }
  }

  /**
   * Waits until the dashboard whose Consul {@code consul} reaches has written that it kept its Core
   * Services at {@code since} or later.
   */
  static void awaitCoreServicesKept(RestClient consul, Instant since) {
    await()
        .atMost(CORE_SERVICES_WAIT)
        .ignoreExceptions()
        .until(
            () -> consulValue(consul, CORE_SERVICES_INITIALIZED),
            value -> value != null && !Instant.parse(value).isBefore(since));
  }

  /** The value at {@code key} in this Workstation's Consul KV, straight from Consul, or null. */
  String consulValue(String key) {
    return consulValue(consulHttp(), key);
  }

  private static String consulValue(RestClient consul, String key) {
    return consul
        .get()
        .uri("/v1/kv/" + key + "?raw")
        .exchange((request, response) ->
            response.getStatusCode() == HttpStatus.NOT_FOUND
                ? null
                : response.bodyTo(String.class));
  }

  /** The port this Workstation's dashboard listens on now. */
  int dashboardPort() {
    return Integer.parseInt(dashboard.getEnvironment().getRequiredProperty("local.server.port"));
  }

  /** The port this Workstation's Consul answers on, as its dashboard reaches it. */
  int consulPort() {
    return consul.getMappedPort(TestcontainersConfiguration.CONSUL_HTTP_PORT);
  }

  private void startDashboard(String... settings) {
    String[] usual = {
      "--server.port=0",
      "--spring.main.banner-mode=off",
      "--lvh-yard.workstation.name=" + NAME,
      "--lvh-yard.workstation.base-domain=" + DashboardTest.BASE_DOMAIN,
      "--lvh-yard.docker.host=" + TestcontainersConfiguration.dockerHost(),
      "--lvh-yard.consul.url=http://" + consul.getHost() + ":" + consulPort(),
      "--lvh-yard.route-profiles.path=" + ROUTE_PROFILES,
      // Its real Peer is a name only the Docker network resolves. Not the Peers of a developer's
      // .env.
      "--lvh-yard.consul.peers="
    };
    // A setting given twice would be read as both values, joined by a comma: settings replace.
    String[] arguments =
        Stream.concat(
                Arrays.stream(usual)
                    .filter(argument ->
                        Arrays.stream(settings)
                            .noneMatch(
                                setting -> setting.startsWith(argument.split("=")[0] + "="))),
                Arrays.stream(settings))
            .toArray(String[]::new);
    dashboard =
        new SpringApplicationBuilder(DashboardApplication.class)
            .initializers(context ->
                context
                    .getBeanFactory()
                    .registerSingleton(
                        SkipTestConfigurations.class.getName(), new SkipTestConfigurations()))
            // Arguments, which win over the .env a developer may have at the repository root.
            .run(arguments);
    http =
        RestClient.builder()
            .baseUrl(
                "http://127.0.0.1:"
                    + dashboard.getEnvironment().getRequiredProperty("local.server.port"))
            .build();
  }

  /** Registers a Static Instance of {@code service} on this Workstation, through its dashboard. */
  JsonNode register(String service, Map<String, Object> instance) {
    return http.post()
        .uri(DashboardTest.instancesPath(service))
        .contentType(MediaType.APPLICATION_JSON)
        .body(instance)
        .retrieve()
        .body(JsonNode.class);
  }

  /**
   * Binds {@code service} on this Workstation to {@code container}'s {@code port}, through its
   * dashboard.
   */
  void bind(String service, String container, int port) {
    http.post()
        .uri(DashboardTest.containerBindingsPath(service))
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("container", container, "port", port))
        .retrieve()
        .toBodilessEntity();
  }

  /** Publishes {@code service} on this Workstation, through its dashboard. */
  void publish(String service) {
    http.put().uri(DashboardTest.publicationPath(service)).retrieve().toBodilessEntity();
  }

  /** Publishes {@code service} on this Workstation with {@code scope}, such as {@code yard}. */
  void publish(String service, String scope) {
    http.put()
        .uri(DashboardTest.publicationPath(service))
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("scope", scope))
        .retrieve()
        .toBodilessEntity();
  }

  /** Publishes {@code service} on this Workstation with {@code publication}, the API's body. */
  void publish(String service, Map<String, Object> publication) {
    http.put()
        .uri(DashboardTest.publicationPath(service))
        .contentType(MediaType.APPLICATION_JSON)
        .body(publication)
        .retrieve()
        .toBodilessEntity();
  }

  /** Unpublishes {@code service} on this Workstation, through its dashboard. */
  void unpublish(String service) {
    http.delete().uri(DashboardTest.publicationPath(service)).retrieve().toBodilessEntity();
  }

  /**
   * Removes the Static Instance {@code id} of {@code service} on this Workstation, through its
   * dashboard.
   */
  void remove(String service, String id) {
    http.delete()
        .uri(DashboardTest.instancesPath(service) + "/" + id)
        .retrieve()
        .toBodilessEntity();
  }

  /** The page at {@code path} of this Workstation's dashboard, as HTML. */
  String page(String path) {
    return http.get().uri(path).accept(MediaType.TEXT_HTML).retrieve().body(String.class);
  }

  /** The answer of this Workstation's dashboard to {@code path}, even an error. */
  ResponseEntity<JsonNode> get(String path) {
    return http.get()
        .uri(path)
        .retrieve()
        .onStatus(status -> true, (request, response) -> {})
        .toEntity(JsonNode.class);
  }

  /** The status of LVH Yard on this Workstation, from its dashboard's API. */
  JsonNode status() {
    return http.get().uri("/api/status").retrieve().body(JsonNode.class);
  }

  /** What this Workstation's Traefik reads from its dashboard. */
  JsonNode traefikConfiguration() {
    return http.get().uri("/traefik/configuration").retrieve().body(JsonNode.class);
  }

  /** One of this Workstation's Services, as its own dashboard sees it. */
  ResponseEntity<JsonNode> service(String service) {
    return http.get().uri(DashboardTest.servicePath(service)).retrieve().toEntity(JsonNode.class);
  }

  /**
   * Writes {@code value} at {@code key} in this Workstation's Consul KV, straight to Consul, so the
   * test can store what its dashboard would refuse.
   */
  void putConsulValue(String key, String value) {
    consulHttp()
        .put()
        .uri("/v1/kv/" + key)
        .contentType(MediaType.TEXT_PLAIN)
        .body(value)
        .retrieve()
        .toBodilessEntity();
  }

  /**
   * Removes the registration {@code id} from this Workstation's Consul agent, straight from Consul,
   * so the test can remove what its dashboard would keep.
   */
  void deregisterInConsul(String id) {
    consulHttp().put().uri("/v1/agent/service/deregister/" + id).retrieve().toBodilessEntity();
  }

  /**
   * Removes the check of the registration {@code id} from this Workstation's Consul agent, straight
   * from Consul, and keeps the registration: Consul names a check given with its registration
   * {@code service:<id>}.
   */
  void deregisterCheckInConsul(String id) {
    consulHttp()
        .put()
        .uri("/v1/agent/check/deregister/service:" + id)
        .retrieve()
        .toBodilessEntity();
  }

  /** Removes {@code key} from this Workstation's Consul KV, straight from Consul. */
  void deleteConsulValue(String key) {
    consulHttp().delete().uri("/v1/kv/" + key).retrieve().toBodilessEntity();
  }

  private RestClient consulHttp() {
    return RestClient.builder()
        .baseUrl(
            "http://"
                + consul.getHost()
                + ":"
                + consul.getMappedPort(TestcontainersConfiguration.CONSUL_HTTP_PORT))
        .build();
  }

  /**
   * Freezes this Workstation's Consul, as a computer that is switched off: nothing it is asked is
   * answered.
   */
  void stopAnswering() {
    answering = false;
    DockerClientFactory.instance().client().pauseContainerCmd(consul.getContainerId()).exec();
  }

  /** Lets this Workstation's Consul answer again, where {@link #stopAnswering} left it. */
  void answerAgain() {
    DockerClientFactory.instance().client().unpauseContainerCmd(consul.getContainerId()).exec();
    answering = true;
  }

  @Override
  public void close() {
    dashboard.close();
    consul.stop();
  }

  /**
   * Keeps the test Workstation's {@link TestConfiguration}s out of this dashboard's component scan,
   * as a {@code @SpringBootTest} does for its own: otherwise this dashboard would start a Yard of
   * its own.
   */
  static final class SkipTestConfigurations extends TypeExcludeFilter {

    @Override
    public boolean match(
        MetadataReader metadataReader, MetadataReaderFactory metadataReaderFactory) {
      return metadataReader
          .getAnnotationMetadata()
          .hasAnnotation(TestConfiguration.class.getName());
    }
  }
}
