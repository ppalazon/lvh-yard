package dev.palazon.lvhyard.dashboard;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

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

    @Test
    void remoteServiceWithYardScopeIsRoutedAtItsWorkstationsHostname() {
        String api = newServiceName("api");
        remote.register(api, Map.of("host", "10.9.8.7", "port", 9090));
        remote.register(api, Map.of("host", "10.9.8.8", "port", 9090));

        remote.publish(api, "yard");

        JsonNode http = get("/traefik/configuration").getBody().get("http");
        assertThat(serverUrlsAt(http, api + "." + RemoteWorkstation.NAME + ".lvh.test"))
                .containsExactly("http://10.9.8.7:9090", "http://10.9.8.8:9090");
        assertThat(routedHostnames(http)).doesNotContain(api + ".lvh.test");
    }

    @Test
    void remoteServiceWithLocalScopeHasNoRoute() {
        String api = newServiceName("api");
        remote.register(api, Map.of("host", "10.9.8.7", "port", 9090));

        remote.publish(api, "local");

        assertThat(routedHostnames(get("/traefik/configuration").getBody().get("http")))
                .noneMatch(hostname -> hostname.startsWith(api + "."));
    }

    @Test
    void ownServiceWithYardScopeKeepsItsShortHostname() {
        String web = newServiceName("web");
        register(web, Map.of("host", "10.1.2.3", "port", 8080));

        put(publicationPath(web), Map.of("scope", "yard"));

        assertThat(serverUrlsAt(get("/traefik/configuration").getBody().get("http"), web + ".lvh.test"))
                .containsExactly("http://10.1.2.3:8080");
    }

    @Test
    void remoteServiceWithTheSameNameAsAnOwnServiceGetsARouteOfItsOwn() {
        String api = newServiceName("api");
        register(api, Map.of("host", "10.1.2.3", "port", 8080));
        publish(api);
        remote.register(api, Map.of("host", "10.9.8.7", "port", 9090));

        remote.publish(api, "yard");

        JsonNode http = get("/traefik/configuration").getBody().get("http");
        assertThat(serverUrlsAt(http, api + ".lvh.test")).containsExactly("http://10.1.2.3:8080");
        assertThat(serverUrlsAt(http, api + "." + RemoteWorkstation.NAME + ".lvh.test"))
                .containsExactly("http://10.9.8.7:9090");
    }

    @Test
    void routesOfAnUnreachableWorkstationAreDroppedAndComeBackByThemselves() {
        String web = newServiceName("web");
        register(web, Map.of("host", "10.1.2.3", "port", 8080));
        publish(web);
        String api = newServiceName("api");
        remote.register(api, Map.of("host", "10.9.8.7", "port", 9090));
        remote.publish(api, "yard");
        String remoteHostname = api + "." + RemoteWorkstation.NAME + ".lvh.test";

        remote.stopAnswering();
        try {
            JsonNode http = await().atMost(Duration.ofSeconds(60))
                    .until(() -> get("/traefik/configuration").getBody().get("http"),
                            configuration -> !routedHostnames(configuration).contains(remoteHostname));

            assertThat(routedHostnames(http)).contains(web + ".lvh.test", "yard.lvh.test");
        } finally {
            remote.answerAgain();
        }

        await().atMost(Duration.ofSeconds(60)).until(
                () -> routedHostnames(get("/traefik/configuration").getBody().get("http")),
                hostnames -> hostnames.contains(remoteHostname));
    }

    /** The hostname of every Route, from its {@code Host(`...`)} rule. */
    private static List<String> routedHostnames(JsonNode http) {
        return http.get("routers").valueStream().map(router -> hostnameOf(router.get("rule").asString())).toList();
    }

    /** Where the Route for {@code hostname} sends requests. */
    private static List<String> serverUrlsAt(JsonNode http, String hostname) {
        JsonNode router = http.get("routers").valueStream()
                .filter(candidate -> hostnameOf(candidate.get("rule").asString()).equals(hostname))
                .findFirst().orElseThrow(() -> new AssertionError("No Route for " + hostname + " in " + http));
        assertThat(router.get("entryPoints").get(0).asString()).isEqualTo("web");
        return serverUrls(http, router.get("service").asString());
    }

    private static String hostnameOf(String rule) {
        return rule.substring("Host(`".length(), rule.length() - "`)".length());
    }

    private static List<String> serverUrls(JsonNode http, String service) {
        return http.get("services").get(service).get("loadBalancer").get("servers").valueStream()
                .map(server -> server.get("url").asString()).toList();
    }
}
