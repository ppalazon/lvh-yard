package dev.palazon.lvhyard.dashboard;

import java.util.List;
import java.util.Map;

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

    @Test
    void publishedServiceIsRoutedToEveryInstance() {
        String grafana = newServiceName("grafana");
        register(grafana, Map.of("host", "10.1.2.3", "port", 3000));
        register(grafana, Map.of("host", "10.1.2.4", "port", 3000));

        publish(grafana);

        JsonNode http = get("/traefik/configuration").getBody().get("http");
        JsonNode router = http.get("routers").get(grafana);
        assertThat(router.get("rule").asString()).isEqualTo("Host(`" + grafana + ".lvh.test`)");
        assertThat(router.get("entryPoints").get(0).asString()).isEqualTo("web");
        assertThat(router.get("service").asString()).isEqualTo(grafana);
        assertThat(serverUrls(http, grafana)).containsExactly("http://10.1.2.3:3000", "http://10.1.2.4:3000");
    }

    @Test
    void instanceRegisteredAfterPublishingIsRouted() {
        String web = newServiceName("web");
        register(web, Map.of("host", "10.1.2.3", "port", 8080));
        publish(web);

        register(web, Map.of("host", "10.1.2.3", "port", 8081));

        assertThat(serverUrls(get("/traefik/configuration").getBody().get("http"), web))
                .containsExactly("http://10.1.2.3:8080", "http://10.1.2.3:8081");
    }

    @Test
    void unpublishedServiceHasNoRoute() {
        String web = newServiceName("web");
        register(web, Map.of("host", "10.1.2.3", "port", 8080));
        publish(web);

        delete(publicationPath(web));

        JsonNode http = get("/traefik/configuration").getBody().get("http");
        assertThat(http.get("routers").has(web)).isFalse();
        assertThat(http.get("services").has(web)).isFalse();
    }

    @Test
    void yardRouteStaysBesidePublishedServices() {
        String web = newServiceName("web");
        register(web, Map.of("host", "10.1.2.3", "port", 8080));
        publish(web);

        JsonNode http = get("/traefik/configuration").getBody().get("http");

        assertThat(http.get("routers").get("yard").get("rule").asString()).isEqualTo("Host(`yard.lvh.test`)");
        assertThat(serverUrls(http, "yard")).containsExactly("http://127.0.0.1:" + port);
    }

    private static List<String> serverUrls(JsonNode http, String service) {
        return http.get("services").get(service).get("loadBalancer").get("servers").valueStream()
                .map(server -> server.get("url").asString()).toList();
    }
}
