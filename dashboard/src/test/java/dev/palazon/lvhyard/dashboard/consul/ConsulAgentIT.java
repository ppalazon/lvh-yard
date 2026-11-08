package dev.palazon.lvhyard.dashboard.consul;

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/** The Consul contract against a real Consul, the version that compose.yaml runs. */
class ConsulAgentIT extends ConsulContract {

  private static GenericContainer<?> container;

  private static Consul consul;

  @BeforeAll
  static void startConsul() {
    container =
        new GenericContainer<>(DockerImageName.parse("hashicorp/consul:2.1.0"))
            .withExposedPorts(8500)
            .withCommand(
                "agent",
                "-dev",
                "-client=0.0.0.0",
                "-bind={{ GetPrivateIP }}",
                "-datacenter=" + DATACENTER,
                "-node=" + DATACENTER)
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
    container.stop();
  }

  @Override
  Consul consul() {
    return consul;
  }
}
