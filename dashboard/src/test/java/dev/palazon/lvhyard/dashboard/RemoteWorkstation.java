package dev.palazon.lvhyard.dashboard;

import static org.awaitility.Awaitility.await;

import dev.palazon.lvhyard.dashboard.consul.Consul.Registration;
import dev.palazon.lvhyard.dashboard.consul.FakeConsul;
import dev.palazon.lvhyard.dashboard.docker.FakeDocker;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
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

  /** Its own Backups folder, apart from the test Workstation's. */
  private static final Path BACKUPS =
      TestcontainersConfiguration.temporaryFolder("lvh-yard-remote-backups");

  /** Where a dashboard writes the time when its first setup is done. */
  static final String FIRST_SETUP = "lvh-yard/first-setup";

  /** The names of the Services that the first setup writes, kept until it is done. */
  static final String FIRST_SETUP_NAMES = "lvh-yard/first-setup-names";

  /** How long a dashboard may take to do its first setup after it started. */
  private static final Duration FIRST_SETUP_WAIT = Duration.ofSeconds(30);

  /** Its real Consul, or null when it runs on {@link #fake}. */
  private final GenericContainer<?> consul;

  /** Its Consul in a component test, federated with the test Workstation's; else null. */
  private final FakeConsul fake;

  /** The Docker that both Workstations share in a component test; else null. */
  private final FakeDocker fakeDocker;

  private ConfigurableApplicationContext dashboard;

  private RestClient http;

  /** Whether this Workstation's Consul answers, as {@link #stopAnswering} leaves it. */
  private boolean answering = true;

  /** Its real Consul just after the first setup, which {@link #reset} puts back. */
  private final ConsulBaseline baseline;

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
    fake = null;
    fakeDocker = null;
    startDashboard();
    awaitFirstSetup(consulHttp());
    baseline = ConsulBaseline.of(consulHttp());
  }

  /**
   * Starts its dashboard on the datacenter {@value #NAME} of {@code yard}, the test Workstation's
   * FakeConsul, and on {@code docker}, the Docker that both Workstations share.
   */
  RemoteWorkstation(FakeConsul yard, FakeDocker docker) {
    consul = null;
    fake = yard.as(NAME);
    fakeDocker = docker;
    baseline = null;
    startDashboard();
    awaitOwnFirstSetup();
  }

  /**
   * Puts this Workstation back as it was just after the first setup: its Consul answers and holds
   * what it held then, and its Backups folder is empty. Answers whether its Consul did not answer,
   * as the test Workstation then needs a while to find it again.
   */
  boolean reset() {
    boolean wasPaused = !answering;
    if (wasPaused) {
      answerAgain();
    }
    if (fake == null) {
      // A component test puts the whole FakeConsul back, this datacenter too.
      baseline.restore(consulHttp());
    }
    TestcontainersConfiguration.empty(BACKUPS);
    return wasPaused;
  }

  /**
   * Stops this Workstation's dashboard, does {@code meanwhile}, and starts the dashboard again.
   * When its Consul answers, it waits until the first setup is done.
   */
  void whileDashboardIsStopped(Runnable meanwhile) {
    whileDashboardIsStopped(meanwhile, new String[0]);
  }

  /**
   * Stops this Workstation's dashboard, does {@code meanwhile}, and starts the dashboard again with
   * {@code settings} too, such as {@code --server.address=0.0.0.0}, which win over the usual ones.
   * When its Consul answers, it waits until the first setup is done.
   */
  void whileDashboardIsStopped(Runnable meanwhile, String... settings) {
    dashboard.close();
    try {
      meanwhile.run();
    } finally {
      startDashboard(settings);
      if (answering) {
        awaitOwnFirstSetup();
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
   * Waits until the Consul that {@code consul} reaches holds the marker of the first setup. When
   * the marker is there at the start, the dashboard writes nothing, so this waits for nothing.
   */
  static void awaitFirstSetup(RestClient consul) {
    await()
        .atMost(FIRST_SETUP_WAIT)
        .ignoreExceptions()
        .until(() -> consulValue(consul, FIRST_SETUP), value -> value != null);
  }

  private void awaitOwnFirstSetup() {
    if (fake == null) {
      awaitFirstSetup(consulHttp());
    } else {
      await()
          .atMost(FIRST_SETUP_WAIT)
          .until(() -> fake.values(null, FIRST_SETUP).containsKey(FIRST_SETUP));
    }
  }

  /** The value at {@code key} in this Workstation's Consul KV, straight from Consul, or null. */
  String consulValue(String key) {
    return fake == null ? consulValue(consulHttp(), key) : fake.values(null, key).get(key);
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

  /** Consul's index of the last write to {@code key} in this Workstation's Consul KV. */
  long consulModifyIndex(String key) {
    if (fake != null) {
      return fake.modifyIndex(key);
    }
    JsonNode entries = consulHttp().get().uri("/v1/kv/" + key).retrieve().body(JsonNode.class);
    return entries.get(0).get("ModifyIndex").asLong();
  }

  /** The port this Workstation's dashboard listens on now. */
  int dashboardPort() {
    return Integer.parseInt(dashboard.getEnvironment().getRequiredProperty("local.server.port"));
  }

  /** The port this Workstation's Consul answers on, as its dashboard reaches it. */
  int consulPort() {
    if (fake != null) {
      throw new UnsupportedOperationException("A fake Consul has no port");
    }
    return consul.getMappedPort(TestcontainersConfiguration.CONSUL_HTTP_PORT);
  }

  private void startDashboard(String... settings) {
    String consulUrl =
        fake == null ? "http://" + consul.getHost() + ":" + consulPort() : "http://127.0.0.1:1";
    String dockerHost =
        fake == null ? TestcontainersConfiguration.dockerHost() : "unix:///fake/docker.sock";
    String[] usual = {
      "--server.port=0",
      "--spring.main.banner-mode=off",
      "--lvh-yard.workstation.name=" + NAME,
      "--lvh-yard.workstation.base-domain=" + DashboardIT.BASE_DOMAIN,
      "--lvh-yard.docker.host=" + dockerHost,
      "--lvh-yard.consul.url=" + consulUrl,
      "--lvh-yard.backups.path=" + BACKUPS,
      "--lvh-yard.bound-instances.check-interval=1s",
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
            .initializers(context -> {
              context
                  .getBeanFactory()
                  .registerSingleton(
                      SkipTestConfigurations.class.getName(), new SkipTestConfigurations());
              if (fake != null) {
                GenericApplicationContext beans = (GenericApplicationContext) context;
                beans.registerBean(
                    "fakeConsul", FakeConsul.class, () -> fake, bean -> bean.setPrimary(true));
                // A test that names a Docker host wants how the dashboard fails on that socket.
                if (Arrays.stream(settings)
                    .noneMatch(setting -> setting.startsWith("--lvh-yard.docker.host="))) {
                  beans.registerBean(
                      "fakeDocker",
                      FakeDocker.class,
                      () -> fakeDocker,
                      bean -> bean.setPrimary(true));
                }
              }
            })
            // Arguments, which win over the .env a developer may have at the repository root.
            .run(arguments);
    http =
        RestClient.builder()
            .baseUrl(
                "http://127.0.0.1:"
                    + dashboard.getEnvironment().getRequiredProperty("local.server.port"))
            .build();
  }

  /**
   * Creates {@code service} on this Workstation, through its dashboard, unless it exists. It stays
   * Unpublished.
   */
  void create(String service) {
    http.post()
        .uri(DashboardIT.SERVICES_PATH)
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("name", service))
        .retrieve()
        .toBodilessEntity();
  }

  /**
   * Registers a Static Instance of {@code service} on this Workstation, through its dashboard. It
   * creates the Service first, if needed.
   */
  JsonNode register(String service, Map<String, Object> instance) {
    create(service);
    return http.post()
        .uri(DashboardIT.instancesPath(service))
        .contentType(MediaType.APPLICATION_JSON)
        .body(instance)
        .retrieve()
        .body(JsonNode.class);
  }

  /**
   * Binds {@code service} on this Workstation to {@code container}'s {@code port}, through its
   * dashboard. It creates the Service first, if needed.
   */
  void bind(String service, String container, int port) {
    create(service);
    http.post()
        .uri(DashboardIT.containerBindingsPath(service))
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("container", container, "port", port))
        .retrieve()
        .toBodilessEntity();
  }

  /** Removes the Container Binding {@code id} of {@code service}, through its dashboard. */
  void unbind(String service, String id) {
    http.delete()
        .uri(DashboardIT.containerBindingsPath(service) + "/" + id)
        .retrieve()
        .toBodilessEntity();
  }

  /** The Backup of this Workstation's setup, as its dashboard exports it. */
  String backup() {
    return http.get()
        .uri("/api/workstation/backup")
        .accept(MediaType.parseMediaType("application/yaml"))
        .retrieve()
        .body(String.class);
  }

  /** Publishes {@code service} on this Workstation, through its dashboard. */
  void publish(String service) {
    create(service);
    http.put().uri(DashboardIT.publicationPath(service)).retrieve().toBodilessEntity();
  }

  /** Publishes {@code service} on this Workstation with {@code scope}, such as {@code yard}. */
  void publish(String service, String scope) {
    create(service);
    http.put()
        .uri(DashboardIT.publicationPath(service))
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("scope", scope))
        .retrieve()
        .toBodilessEntity();
  }

  /** Publishes {@code service} on this Workstation with {@code publication}, the API's body. */
  void publish(String service, Map<String, Object> publication) {
    create(service);
    http.put()
        .uri(DashboardIT.publicationPath(service))
        .contentType(MediaType.APPLICATION_JSON)
        .body(publication)
        .retrieve()
        .toBodilessEntity();
  }

  /** Unpublishes {@code service} on this Workstation, through its dashboard. */
  void unpublish(String service) {
    http.delete().uri(DashboardIT.publicationPath(service)).retrieve().toBodilessEntity();
  }

  /**
   * Removes the Static Instance {@code id} of {@code service} on this Workstation, through its
   * dashboard.
   */
  void remove(String service, String id) {
    http.delete().uri(DashboardIT.instancesPath(service) + "/" + id).retrieve().toBodilessEntity();
  }

  /** Deletes {@code service} on this Workstation, through its dashboard. */
  void delete(String service) {
    http.delete().uri(DashboardIT.servicePath(service)).retrieve().toBodilessEntity();
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

  /** Adds an Alias to this Workstation's {@code service}, through its dashboard. */
  void addAlias(String service, String workstation, String remoteService) {
    http.post()
        .uri(AliasesComponentTest.aliasesPath(service))
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("workstation", workstation, "service", remoteService))
        .retrieve()
        .toBodilessEntity();
  }

  /** Sets the order or the toggle of {@code service}'s Instance Priority, through its dashboard. */
  void setInstancePriority(String service, Map<String, Object> change) {
    http.put()
        .uri(DashboardIT.instancePriorityPath(service))
        .contentType(MediaType.APPLICATION_JSON)
        .body(change)
        .retrieve()
        .toBodilessEntity();
  }

  /** One of this Workstation's Services, as its own dashboard sees it. */
  ResponseEntity<JsonNode> service(String service) {
    return http.get().uri(DashboardIT.servicePath(service)).retrieve().toEntity(JsonNode.class);
  }

  /**
   * Writes {@code value} at {@code key} in this Workstation's Consul KV, straight to Consul, so the
   * test can store what its dashboard would refuse.
   */
  void putConsulValue(String key, String value) {
    if (fake != null) {
      fake.putValue(key, value);
      return;
    }
    consulHttp()
        .put()
        .uri("/v1/kv/" + key)
        .contentType(MediaType.TEXT_PLAIN)
        .body(value)
        .retrieve()
        .toBodilessEntity();
  }

  /**
   * Registers {@code id} of {@code service} at {@code host} and {@code port} on this Workstation's
   * Consul agent, straight to Consul, so the test can write it while the dashboard is stopped.
   */
  void registerInConsul(String id, String service, String host, int port) {
    if (fake != null) {
      fake.register(new Registration(id, service, host, port, List.of(), Map.of(), null));
      return;
    }
    consulHttp()
        .put()
        .uri("/v1/agent/service/register")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("ID", id, "Name", service, "Address", host, "Port", port))
        .retrieve()
        .toBodilessEntity();
  }

  /**
   * Removes the registration {@code id} from this Workstation's Consul agent, straight from Consul,
   * so the test can remove it while the dashboard is stopped.
   */
  void deregisterInConsul(String id) {
    if (fake != null) {
      fake.deregister(id);
      return;
    }
    consulHttp().put().uri("/v1/agent/service/deregister/" + id).retrieve().toBodilessEntity();
  }

  /**
   * Removes the check of the registration {@code id} from this Workstation's Consul agent, straight
   * from Consul, and keeps the registration: Consul names a check given with its registration
   * {@code service:<id>}.
   */
  void deregisterCheckInConsul(String id) {
    if (fake != null) {
      fake.removeCheck(id);
      return;
    }
    consulHttp()
        .put()
        .uri("/v1/agent/check/deregister/service:" + id)
        .retrieve()
        .toBodilessEntity();
  }

  /**
   * Adds or replaces Route Profiles of this Workstation through its dashboard, as {@code PUT
   * /api/workstation/route-profiles} takes them.
   */
  void putRouteProfiles(Object profiles) {
    http.put()
        .uri(DashboardIT.ROUTE_PROFILES_PATH)
        .contentType(MediaType.APPLICATION_JSON)
        .body(profiles)
        .retrieve()
        .toBodilessEntity();
  }

  /** Removes every Route Profile of this Workstation straight from Consul, Publications or not. */
  void removeAllRouteProfiles() {
    if (fake != null) {
      fake.values(null, "lvh-yard/route-profiles/").keySet().forEach(fake::deleteValue);
      return;
    }
    consulHttp()
        .delete()
        .uri("/v1/kv/lvh-yard/route-profiles/?recurse")
        .retrieve()
        .toBodilessEntity();
  }

  /** Removes {@code key} from this Workstation's Consul KV, straight from Consul. */
  void deleteConsulValue(String key) {
    if (fake != null) {
      fake.deleteValue(key);
      return;
    }
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
    if (fake != null) {
      answering = false;
      fake.datacenter(NAME).stopAnswering();
      return;
    }
    answering = false;
    DockerClientFactory.instance().client().pauseContainerCmd(consul.getContainerId()).exec();
  }

  /** Lets this Workstation's Consul answer again, where {@link #stopAnswering} left it. */
  void answerAgain() {
    if (fake != null) {
      fake.datacenter(NAME).answerAgain();
      answering = true;
      return;
    }
    DockerClientFactory.instance().client().unpauseContainerCmd(consul.getContainerId()).exec();
    answering = true;
  }

  @Override
  public void close() {
    dashboard.close();
    if (consul != null) {
      consul.stop();
    }
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
