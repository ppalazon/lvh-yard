package cc.lvhy.yard.dashboard.consul;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/** The Consul contract against a real Consul, the version that compose.yaml runs. */
class ConsulAgentIT extends ConsulContract {

  private static GenericContainer<?> container;

  private static final Network NETWORK = Network.newNetwork();

  private static final List<GenericContainer<?>> LISTENERS = new ArrayList<>();

  private static Consul consul;

  @BeforeAll
  static void startConsul() {
    container =
        new GenericContainer<>(DockerImageName.parse("hashicorp/consul:2.1.0"))
            .withNetwork(NETWORK)
            .withExposedPorts(8500)
            .withCommand(
                "agent",
                "-dev",
                "-client=0.0.0.0",
                "-bind={{ GetPrivateIP }}",
                "-datacenter=" + DATACENTER,
                "-node=" + DATACENTER,
                // Check output reaches the catalog at once, as compose.yaml sets it.
                "-hcl=check_update_interval = \"0s\"")
            // The leader is "" until the server has elected itself.
            .waitingFor(
                Wait.forHttp("/v1/status/leader")
                    .forPort(8500)
                    .forResponsePredicate(leader -> leader.length() > 2));
    container.start();
    consul =
        new ConsulAgent(
            new ConsulSettings(
                URI.create("http://" + container.getHost() + ":" + container.getMappedPort(8500)),
                "",
                Duration.ofSeconds(3),
                Duration.ofSeconds(10)));
  }

  @AfterAll
  static void stopConsul() {
    LISTENERS.forEach(GenericContainer::stop);
    container.stop();
    NETWORK.close();
  }

  @Override
  String listening() {
    return start("while true; do nc -l -p 80 >/dev/null; done");
  }

  @Override
  String silent() {
    // The container listens on port 80 only.
    return listening().replace(":80", ":81");
  }

  @Override
  String answeringHttp(String statusLine, String body) {
    String response =
        "HTTP/1.1 "
            + statusLine
            + "\\r\\nContent-Length: "
            + body.length()
            + "\\r\\nConnection: close\\r\\n\\r\\n"
            + body;
    return start("while true; do printf '" + response + "' | nc -l -p 80 >/dev/null; done");
  }

  /** Starts a container on Consul's network that runs {@code script}, and answers its port 80. */
  private static String start(String script) {
    String alias = "listener-" + LISTENERS.size();
    GenericContainer<?> listener =
        new GenericContainer<>(DockerImageName.parse("busybox:1.37"))
            .withNetwork(NETWORK)
            .withNetworkAliases(alias)
            .withCommand("sh", "-c", script);
    LISTENERS.add(listener);
    listener.start();
    return listener
            .getContainerInfo()
            .getNetworkSettings()
            .getNetworks()
            .values()
            .iterator()
            .next()
            .getIpAddress()
        + ":80";
  }

  @Override
  Consul consul() {
    return consul;
  }
}
