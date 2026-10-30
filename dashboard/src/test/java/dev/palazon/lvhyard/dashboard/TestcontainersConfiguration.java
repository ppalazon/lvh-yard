package dev.palazon.lvhyard.dashboard;

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
 * The real infrastructure behind the dashboard under test, started once and shared by every test class: a Yard of two
 * Workstations, the test Workstation's Consul and a {@link RemoteWorkstation}, federated over one Docker network.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    /** The version compose.yaml runs. */
    private static final DockerImageName CONSUL_IMAGE = DockerImageName.parse("hashicorp/consul:2.1.0");

    static final int CONSUL_HTTP_PORT = 8500;

    /** Where the {@link RemoteWorkstation} finds the test Workstation's Consul on the network, at its serf WAN port. */
    private static final String CONSUL_HOSTNAME = "consul-" + DashboardTest.WORKSTATION;

    @Bean(destroyMethod = "close")
    Network yardNetwork() {
        return Network.newNetwork();
    }

    /**
     * The test Workstation's Consul, as one dev-mode server. It binds to the container's own address, which then
     * stands for the Workstation's LAN address, as {@code -bind} does in compose.yaml. Check output reaches the
     * catalog at once, as compose.yaml sets it.
     */
    @Bean(initMethod = "start", destroyMethod = "stop")
    GenericContainer<?> consulContainer(Network yardNetwork) {
        return consul().withNetwork(yardNetwork).withNetworkAliases(CONSUL_HOSTNAME)
                .withCommand(consulCommand(DashboardTest.WORKSTATION));
    }

    @Bean(destroyMethod = "close")
    RemoteWorkstation remoteWorkstation(Network yardNetwork, GenericContainer<?> consulContainer) {
        return new RemoteWorkstation(yardNetwork, CONSUL_HOSTNAME + ":8302");
    }

    @Bean
    DynamicPropertyRegistrar consulProperties(GenericContainer<?> consulContainer) {
        return registry -> {
            registry.add("lvh-yard.consul.url", () -> "http://" + consulContainer.getHost() + ":"
                    + consulContainer.getMappedPort(CONSUL_HTTP_PORT));
            registry.add("lvh-yard.docker.host", TestcontainersConfiguration::dockerHost);
        };
    }

    /** The Docker the tests start their containers on, which the dashboards follow too. */
    static String dockerHost() {
        return DockerClientFactory.instance().getTransportConfig().getDockerHost().toString();
    }

    /**
     * A container called {@code name} that accepts TCP connections on {@code port}, as a Service in a container
     * would. It uses the Consul image only because its shell has {@code nc}, and the tests pull it anyway. Its
     * {@code nc} ends after each connection, even with {@code -k}, so a loop starts it again.
     */
    static GenericContainer<?> listener(String name, int port) {
        return new GenericContainer<>(CONSUL_IMAGE)
                .withCreateContainerCmdModifier(command -> command.withName(name)
                        .withEntrypoint("sh", "-c", "while true; do nc -l -p " + port + " >/dev/null; done")
                        .withCmd())
                .waitingFor(Wait.forListeningPort());
    }

    /** A Consul container, ready once its server has elected itself. */
    static GenericContainer<?> consul() {
        return new GenericContainer<>(CONSUL_IMAGE)
                .withExposedPorts(CONSUL_HTTP_PORT)
                // The leader is "" until the server has elected itself, and registrations fail until then.
                .waitingFor(Wait.forHttp("/v1/status/leader").forPort(CONSUL_HTTP_PORT)
                        .forResponsePredicate(leader -> leader.length() > 2));
    }

    /** The command of a dev-mode server whose datacenter and node are both {@code workstation}. */
    static String[] consulCommand(String workstation, String... more) {
        List<String> command = new ArrayList<>(List.of("agent", "-dev", "-client=0.0.0.0",
                "-bind={{ GetPrivateIP }}", "-datacenter=" + workstation, "-node=" + workstation,
                "-hcl=check_update_interval = \"0s\""));
        command.addAll(List.of(more));
        return command.toArray(String[]::new);
    }
}
