package dev.palazon.lvhyard.dashboard;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;

/** What Traefik's HTTP provider reads from the dashboard. */
class TraefikConfigurationTest extends DashboardTest {

    @Test
    void routesYardHostnameToTheDashboard() {
        ResponseEntity<JsonNode> response = get("/traefik/configuration");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode http = response.getBody().get("http");
        JsonNode router = http.get("routers").get("yard");
        assertThat(router.get("rule").asString()).isEqualTo("Host(`yard.lvh.test`)");
        assertThat(router.get("entryPoints").get(0).asString()).isEqualTo("web");
        assertThat(router.get("service").asString()).isEqualTo("yard");
        JsonNode server = http.get("services").get("yard").get("loadBalancer").get("servers").get(0);
        assertThat(server.get("url").asString()).isEqualTo("http://127.0.0.1:" + port);
    }
}
