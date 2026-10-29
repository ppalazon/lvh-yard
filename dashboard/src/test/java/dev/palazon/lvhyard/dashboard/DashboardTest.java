package dev.palazon.lvhyard.dashboard;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.JsonNode;

/** The single test seam: the running dashboard as a black box, on Workstation {@code ws-test}. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "lvh-yard.workstation.name=" + DashboardTest.WORKSTATION,
        "lvh-yard.workstation.base-domain=" + DashboardTest.BASE_DOMAIN })
abstract class DashboardTest {

    static final String WORKSTATION = "ws-test";

    static final String BASE_DOMAIN = "lvh.test";

    @LocalServerPort
    int port;

    private RestClient http;

    @BeforeEach
    void connect() {
        http = RestClient.builder()
                .baseUrl("http://127.0.0.1:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> { })
                .build();
    }

    ResponseEntity<JsonNode> get(String path) {
        return http.get().uri(path).retrieve().toEntity(JsonNode.class);
    }

    ResponseEntity<String> getPage(String path) {
        return http.get().uri(path).retrieve().toEntity(String.class);
    }
}
