package dev.palazon.lvhyard.dashboard;

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
 * The test Yard's other Workstation, {@value #NAME}: its own Consul datacenter, joined to the test Workstation's by
 * WAN federation, and its own dashboard, which the tests change it through as its developer would.
 */
final class RemoteWorkstation implements AutoCloseable {

    static final String NAME = "ws-remote";

    private final GenericContainer<?> consul;

    private final ConfigurableApplicationContext dashboard;

    private final RestClient http;

    /** Starts the Workstation's Consul, joined to the Consul at {@code peer} on {@code network}, and its dashboard. */
    RemoteWorkstation(Network network, String peer) {
        consul = TestcontainersConfiguration.consul().withNetwork(network)
                .withCommand(TestcontainersConfiguration.consulCommand(NAME, "-retry-join-wan=" + peer));
        consul.start();
        dashboard = new SpringApplicationBuilder(DashboardApplication.class)
                .initializers(context -> context.getBeanFactory()
                        .registerSingleton(SkipTestConfigurations.class.getName(), new SkipTestConfigurations()))
                // Arguments, which win over the .env a developer may have at the repository root.
                .run("--server.port=0", "--spring.main.banner-mode=off",
                        "--lvh-yard.workstation.name=" + NAME,
                        "--lvh-yard.workstation.base-domain=" + DashboardTest.BASE_DOMAIN,
                        "--lvh-yard.consul.url=http://" + consul.getHost() + ":"
                                + consul.getMappedPort(TestcontainersConfiguration.CONSUL_HTTP_PORT));
        http = RestClient.builder()
                .baseUrl("http://127.0.0.1:" + dashboard.getEnvironment().getRequiredProperty("local.server.port"))
                .build();
    }

    /** Registers a Static Instance of {@code service} on this Workstation, through its dashboard. */
    JsonNode register(String service, Map<String, Object> instance) {
        return http.post().uri(DashboardTest.instancesPath(service)).contentType(MediaType.APPLICATION_JSON)
                .body(instance).retrieve().body(JsonNode.class);
    }

    /** Publishes {@code service} on this Workstation, through its dashboard. */
    void publish(String service) {
        http.put().uri(DashboardTest.publicationPath(service)).retrieve().toBodilessEntity();
    }

    /** One of this Workstation's Services, as its own dashboard sees it. */
    ResponseEntity<JsonNode> service(String service) {
        return http.get().uri(DashboardTest.servicePath(service)).retrieve().toEntity(JsonNode.class);
    }

    /** Freezes this Workstation's Consul, as a computer that is switched off: nothing it is asked is answered. */
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
     * Keeps the test Workstation's {@link TestConfiguration}s out of this dashboard's component scan, as a
     * {@code @SpringBootTest} does for its own: otherwise this dashboard would start a Yard of its own.
     */
    static final class SkipTestConfigurations extends TypeExcludeFilter {

        @Override
        public boolean match(MetadataReader metadataReader, MetadataReaderFactory metadataReaderFactory) {
            return metadataReader.getAnnotationMetadata().hasAnnotation(TestConfiguration.class.getName());
        }
    }
}
