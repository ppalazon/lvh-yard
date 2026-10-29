package dev.palazon.lvhyard.dashboard;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;

import tools.jackson.databind.JsonNode;

/**
 * The single test seam: the running dashboard as a black box, on Workstation {@code ws-test}, with a real Consul
 * behind it. The Consul is shared by every test, so each test registers Services under names of its own
 * ({@link #newServiceName}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "lvh-yard.workstation.name=" + DashboardTest.WORKSTATION,
        "lvh-yard.workstation.base-domain=" + DashboardTest.BASE_DOMAIN })
@Import(TestcontainersConfiguration.class)
abstract class DashboardTest {

    static final String WORKSTATION = "ws-test";

    static final String BASE_DOMAIN = "lvh.test";

    private static final AtomicInteger SERVICE_NAMES = new AtomicInteger();

    @LocalServerPort
    int port;

    @Autowired
    private GenericContainer<?> consulContainer;

    private RestClient http;

    @BeforeEach
    void connect() {
        http = RestClient.builder()
                .baseUrl("http://127.0.0.1:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> { })
                .build();
    }

    /** A Service name no other test uses, such as {@code grafana-3}. */
    static String newServiceName(String prefix) {
        return prefix + "-" + SERVICE_NAMES.incrementAndGet();
    }

    /** The test Workstation's LAN address: the address its Consul binds to. */
    String lanAddress() {
        return consulContainer.getContainerInfo().getNetworkSettings().getNetworks().values().iterator().next().getIpAddress();
    }

    /**
     * Starts a process that accepts TCP connections on {@code port} at the {@link #lanAddress}, as a Service on the
     * Workstation would. It runs until the Consul container stops.
     */
    void listenOnLanAddress(int port) {
        try {
            consulContainer.execInContainer("sh", "-c", "nc -lk -p " + port + " >/dev/null 2>&1 &");
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException("Could not listen on port " + port, e);
        }
    }

    ResponseEntity<JsonNode> get(String path) {
        return http.get().uri(path).retrieve().toEntity(JsonNode.class);
    }

    ResponseEntity<String> getPage(String path) {
        return http.get().uri(path).retrieve().toEntity(String.class);
    }

    ResponseEntity<JsonNode> post(String path, Object body) {
        return http.post().uri(path).contentType(MediaType.APPLICATION_JSON).body(body).retrieve()
                .toEntity(JsonNode.class);
    }

    ResponseEntity<JsonNode> delete(String path) {
        return http.delete().uri(path).retrieve().toEntity(JsonNode.class);
    }

    /** A form submitted by a browser. Redirects are not followed, so the answer to the post itself is seen. */
    ResponseEntity<String> postForm(String path, Map<String, String> fields) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        fields.forEach(form::add);
        return http.post().uri(path).contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve()
                .toEntity(String.class);
    }

    /** Registers a Static Instance of {@code service} on this Workstation through the REST API, and checks it took. */
    JsonNode register(String service, Map<String, Object> instance) {
        ResponseEntity<JsonNode> response = post(instancesPath(service), instance);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new AssertionError("Registering " + service + " answered " + response);
        }
        return response.getBody();
    }

    static String servicePath(String service) {
        return "/api/services/" + WORKSTATION + "/" + service;
    }

    static String instancesPath(String service) {
        return servicePath(service) + "/instances";
    }
}
