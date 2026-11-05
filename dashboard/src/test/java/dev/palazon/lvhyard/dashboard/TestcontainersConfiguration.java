package dev.palazon.lvhyard.dashboard;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * The real infrastructure behind the dashboard under test, started once and shared by every test
 * class: a Yard of two Workstations, the test Workstation's Consul and a {@link RemoteWorkstation},
 * federated over one Docker network.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

  /** The version compose.yaml runs. */
  private static final DockerImageName CONSUL_IMAGE =
      DockerImageName.parse("hashicorp/consul:2.1.0");

  static final int CONSUL_HTTP_PORT = 8500;

  /** The version compose.yaml runs. */
  private static final DockerImageName TRAEFIK_IMAGE = DockerImageName.parse("traefik:v3.7.16");

  /** Two free ports, taken together: one by one, the system may give the same port twice. */
  private static final int[] TRAEFIK_PORTS = freePorts(2);

  /**
   * The loopback port {@link #traefik} listens on, which the dashboard is told before it starts, as
   * compose.yaml and {@code YARD_HTTP_PORT} tell it.
   */
  static final int TRAEFIK_PORT = TRAEFIK_PORTS[0];

  /**
   * The loopback port of {@link #traefik}'s admin entry point, with its ping, which the dashboard
   * is told before it starts, as compose.yaml and {@code YARD_TRAEFIK_ADMIN_PORT} tell it. Not
   * 8080, where a developer's own Traefik may answer.
   */
  static final int TRAEFIK_ADMIN_PORT = TRAEFIK_PORTS[1];

  /**
   * Where the {@link RemoteWorkstation} finds the test Workstation's Consul on the network, at its
   * serf WAN port.
   */
  private static final String CONSUL_HOSTNAME = "consul-" + DashboardTest.WORKSTATION;

  @Bean(destroyMethod = "close")
  Network yardNetwork() {
    return Network.newNetwork();
  }

  /**
   * The test Workstation's Consul, as one dev-mode server. It binds to the container's own address,
   * which then stands for the Workstation's LAN address, as {@code -bind} does in compose.yaml.
   * Check output reaches the catalog at once, as compose.yaml sets it.
   */
  @Bean(initMethod = "start", destroyMethod = "stop")
  GenericContainer<?> consulContainer(Network yardNetwork) {
    return consul()
        .withNetwork(yardNetwork)
        .withNetworkAliases(CONSUL_HOSTNAME)
        .withCommand(consulCommand(DashboardTest.WORKSTATION));
  }

  @Bean(destroyMethod = "close")
  RemoteWorkstation remoteWorkstation(Network yardNetwork, GenericContainer<?> consulContainer) {
    return new RemoteWorkstation(yardNetwork, CONSUL_HOSTNAME + ":8302");
  }

  @Bean
  DynamicPropertyRegistrar consulProperties(GenericContainer<?> consulContainer) {
    return registry -> {
      registry.add(
          "lvh-yard.consul.url",
          () ->
              "http://"
                  + consulContainer.getHost()
                  + ":"
                  + consulContainer.getMappedPort(CONSUL_HTTP_PORT));
      registry.add("lvh-yard.docker.host", TestcontainersConfiguration::dockerHost);
      registry.add("lvh-yard.traefik.port", () -> TRAEFIK_PORT);
      registry.add("lvh-yard.traefik.admin-port", () -> TRAEFIK_ADMIN_PORT);
    };
  }

  /** The Docker the tests start their containers on, which the dashboards follow too. */
  static String dockerHost() {
    return DockerClientFactory.instance().getTransportConfig().getDockerHost().toString();
  }

  /**
   * A container called {@code name} that accepts TCP connections on {@code port}, as a Service in a
   * container would. It uses the Consul image only because its shell has {@code nc}, and the tests
   * pull it anyway. Its {@code nc} ends after each connection, even with {@code -k}, so a loop
   * starts it again.
   */
  static GenericContainer<?> listener(String name, int port) {
    return new GenericContainer<>(CONSUL_IMAGE)
        .withCreateContainerCmdModifier(command ->
            command
                .withName(name)
                .withEntrypoint("sh", "-c", "while true; do nc -l -p " + port + " >/dev/null; done")
                .withCmd())
        .waitingFor(Wait.forListeningPort());
  }

  /**
   * As {@link #listener}, but it answers every HTTP request on {@code port} with 200 and the text
   * {@code body}.
   */
  static GenericContainer<?> httpListener(String name, int port, String body) {
    String response =
        "HTTP/1.1 200 OK\\r\\nContent-Type: text/plain\\r\\nContent-Length: "
            + body.length()
            + "\\r\\nConnection: close\\r\\n\\r\\n"
            + body;
    return new GenericContainer<>(CONSUL_IMAGE)
        .withCreateContainerCmdModifier(command ->
            command
                .withName(name)
                .withEntrypoint(
                    "sh",
                    "-c",
                    "while true; do printf '"
                        + response
                        + "' | nc -l -p "
                        + port
                        + " >/dev/null; done")
                .withCmd())
        .waitingFor(Wait.forListeningPort());
  }

  /**
   * Traefik as compose.yaml runs it, on the host's network and on loopback at {@link
   * #TRAEFIK_PORT}, with its ping at {@link #TRAEFIK_ADMIN_PORT}, reading its Routes from the
   * dashboard at {@code dashboardPort}. It polls every second, so tests wait less for a new Route.
   * It is started only once the dashboard's port is known, by the tests that need it.
   */
  static GenericContainer<?> traefik(int dashboardPort) {
    return traefik(
        TRAEFIK_PORT,
        TRAEFIK_ADMIN_PORT,
        "http://127.0.0.1:" + dashboardPort + "/traefik/configuration");
  }

  /**
   * Traefik on the host's network, on loopback at {@code port}, with its ping on its admin entry
   * point at {@code adminPort}, reading its Routes from {@code configuration} every second.
   */
  static GenericContainer<?> traefik(int port, int adminPort, String configuration) {
    return new GenericContainer<>(TRAEFIK_IMAGE)
        .withNetworkMode("host")
        .withCommand(
            "--entryPoints.web.address=127.0.0.1:" + port,
            "--entryPoints.traefik.address=127.0.0.1:" + adminPort,
            "--ping=true",
            "--ping.entryPoint=traefik",
            "--providers.http.endpoint=" + configuration,
            "--providers.http.pollInterval=1s",
            "--log.level=INFO")
        .waitingFor(Wait.forLogMessage(".*Starting provider \\*http\\.Provider.*", 1));
  }

  /** {@code count} free ports, taken together, so that no two are the same. */
  static int[] freePorts(int count) {
    List<ServerSocket> sockets = new ArrayList<>();
    try {
      for (int i = 0; i < count; i++) {
        sockets.add(new ServerSocket(0));
      }
      return sockets.stream().mapToInt(ServerSocket::getLocalPort).toArray();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } finally {
      for (ServerSocket socket : sockets) {
        try {
          socket.close();
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      }
    }
  }

  /** A Consul container, ready once its server has elected itself. */
  static GenericContainer<?> consul() {
    return new GenericContainer<>(CONSUL_IMAGE)
        .withExposedPorts(CONSUL_HTTP_PORT)
        // The leader is "" until the server has elected itself, and registrations fail until then.
        .waitingFor(
            Wait.forHttp("/v1/status/leader")
                .forPort(CONSUL_HTTP_PORT)
                .forResponsePredicate(leader -> leader.length() > 2));
  }

  /** The command of a dev-mode server whose datacenter and node are both {@code workstation}. */
  static String[] consulCommand(String workstation, String... more) {
    List<String> command =
        new ArrayList<>(
            List.of(
                "agent",
                "-dev",
                "-client=0.0.0.0",
                "-bind={{ GetPrivateIP }}",
                "-datacenter=" + workstation,
                "-node=" + workstation,
                "-hcl=check_update_interval = \"0s\""));
    command.addAll(List.of(more));
    return command.toArray(String[]::new);
  }
}
