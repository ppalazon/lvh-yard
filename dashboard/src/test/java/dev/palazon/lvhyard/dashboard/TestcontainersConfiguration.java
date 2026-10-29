package dev.palazon.lvhyard.dashboard;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/** The real infrastructure behind the dashboard under test, started once and shared by every test class. */
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    /** The version compose.yaml runs. */
    private static final DockerImageName CONSUL_IMAGE = DockerImageName.parse("hashicorp/consul:2.1.0");

    static final int CONSUL_HTTP_PORT = 8500;

    /**
     * The test Workstation's Consul, as one dev-mode server. It binds to the container's own address, which then
     * stands for the Workstation's LAN address, as {@code -bind} does in compose.yaml.
     */
    @Bean(initMethod = "start", destroyMethod = "stop")
    GenericContainer<?> consulContainer() {
        return new GenericContainer<>(CONSUL_IMAGE)
                .withCommand("agent", "-dev", "-client=0.0.0.0", "-bind={{ GetPrivateIP }}",
                        "-datacenter=" + DashboardTest.WORKSTATION, "-node=" + DashboardTest.WORKSTATION)
                .withExposedPorts(CONSUL_HTTP_PORT)
                // The leader is "" until the server has elected itself, and registrations fail until then.
                .waitingFor(Wait.forHttp("/v1/status/leader").forPort(CONSUL_HTTP_PORT)
                        .forResponsePredicate(leader -> leader.length() > 2));
    }

    @Bean
    DynamicPropertyRegistrar consulProperties(GenericContainer<?> consulContainer) {
        return registry -> registry.add("lvh-yard.consul.url",
                () -> "http://" + consulContainer.getHost() + ":" + consulContainer.getMappedPort(CONSUL_HTTP_PORT));
    }
}
