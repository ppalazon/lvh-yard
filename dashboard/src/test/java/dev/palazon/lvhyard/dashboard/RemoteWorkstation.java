package dev.palazon.lvhyard.dashboard;

import java.util.Arrays;
import java.util.Map;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
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

  private final GenericContainer<?> consul;

  private ConfigurableApplicationContext dashboard;

  private RestClient http;

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
    startDashboard();
  }

  /** Stops this Workstation's dashboard, does {@code meanwhile}, and starts the dashboard again. */
  void whileDashboardIsStopped(Runnable meanwhile) {
    whileDashboardIsStopped(meanwhile, new String[0]);
  }

  /**
   * Stops this Workstation's dashboard, does {@code meanwhile}, and starts the dashboard again with
   * {@code settings} too, such as {@code --server.address=0.0.0.0}, which win over the usual ones.
   */
  void whileDashboardIsStopped(Runnable meanwhile, String... settings) {
    dashboard.close();
    try {
      meanwhile.run();
    } finally {
      startDashboard(settings);
    }
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
      "--lvh-yard.consul.url=http://" + consul.getHost() + ":" + consulPort()
    };
    String[] arguments = Arrays.copyOf(usual, usual.length + settings.length);
    System.arraycopy(settings, 0, arguments, usual.length, settings.length);
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
    DockerClientFactory.instance().client().pauseContainerCmd(consul.getContainerId()).exec();
  }

  /** Lets this Workstation's Consul answer again, where {@link #stopAnswering} left it. */
  void answerAgain() {
    DockerClientFactory.instance().client().unpauseContainerCmd(consul.getContainerId()).exec();
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
