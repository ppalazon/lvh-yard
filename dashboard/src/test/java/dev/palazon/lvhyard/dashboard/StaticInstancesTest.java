package dev.palazon.lvhyard.dashboard;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** Static Instances, registered and removed through the REST API. */
class StaticInstancesTest extends DashboardTest {

    /** Long enough for a check's first run, which Consul starts at a random point within its 10 s interval. */
    private static final Duration CHECK_TIMEOUT = Duration.ofSeconds(20);

    @Test
    void registeredInstanceIsListedWithItsService() {
        String grafana = newServiceName("grafana");

        ResponseEntity<JsonNode> response = post(instancesPath(grafana), Map.of("host", "10.1.2.3", "port", 3000));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode service = findService(get("/api/services").getBody(), grafana);
        assertThat(service.get("workstation").asString()).isEqualTo(WORKSTATION);
        JsonNode instance = service.get("instances").get(0);
        assertThat(instance.get("host").asString()).isEqualTo("10.1.2.3");
        assertThat(instance.get("port").asInt()).isEqualTo(3000);
    }

    @Test
    void serviceCanHaveMoreThanOneInstance() {
        String web = newServiceName("web");

        register(web, Map.of("host", "10.1.2.3", "port", 8080));
        register(web, Map.of("host", "10.1.2.3", "port", 8081));

        JsonNode instances = get(servicePath(web)).getBody().get("instances");
        assertThat(instances.valueStream().map(instance -> instance.get("port").asInt())).containsExactly(8080, 8081);
    }

    @ParameterizedTest
    @ValueSource(strings = { "Grafana", "my_api", "api.v2", "-api", "api-",
            "a-label-of-sixty-four-characters-is-one-too-long-for-the-dns-xyz" })
    void serviceNameThatIsNotOneDnsLabelIsRefused(String name) {
        ResponseEntity<JsonNode> response = post(instancesPath(name), Map.of("port", 8080));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("detail").asString()).contains("single DNS label");
        assertThat(findService(get("/api/services").getBody(), name)).isNull();
    }

    @Test
    void serviceNameYardIsRefused() {
        ResponseEntity<JsonNode> response = post(instancesPath("yard"), Map.of("port", 8080));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("detail").asString()).contains("yard").contains("dashboard");
    }

    @ParameterizedTest
    @ValueSource(ints = { 0, 65536 })
    void portOutsideTheTcpRangeIsRefused(int port) {
        String api = newServiceName("api");

        ResponseEntity<JsonNode> response = post(instancesPath(api), Map.of("port", port));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("detail").asString()).contains("1 to 65535");
    }

    @Test
    void portIsRequired() {
        String api = newServiceName("api");

        ResponseEntity<JsonNode> response = post(instancesPath(api), Map.of("host", "10.1.2.3"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("detail").asString()).contains("port");
    }

    @Test
    void registeringOnAnotherWorkstationIsRefused() {
        String api = newServiceName("api");

        ResponseEntity<JsonNode> response = post("/api/services/ws-other/" + api + "/instances",
                Map.of("port", 8080));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().get("detail").asString()).contains("ws-other");
        assertThat(findService(get("/api/services").getBody(), api)).isNull();
    }

    @Test
    void removedInstanceIsGoneAndItsServiceKeepsTheOthers() {
        String web = newServiceName("web");
        String first = register(web, Map.of("host", "10.1.2.3", "port", 8080)).get("id").asString();
        register(web, Map.of("host", "10.1.2.3", "port", 8081));

        ResponseEntity<JsonNode> response = delete(instancesPath(web) + "/" + first);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        JsonNode instances = get(servicePath(web)).getBody().get("instances");
        assertThat(instances.valueStream().map(instance -> instance.get("port").asInt())).containsExactly(8081);
    }

    @Test
    void serviceWithItsLastInstanceRemovedLeavesTheCatalog() {
        String web = newServiceName("web");
        String id = register(web, Map.of("port", 8080)).get("id").asString();

        delete(instancesPath(web) + "/" + id);

        assertThat(findService(get("/api/services").getBody(), web)).isNull();
        assertThat(get(servicePath(web)).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void removingAnUnknownInstanceIsNotFound() {
        String web = newServiceName("web");

        ResponseEntity<JsonNode> response = delete(instancesPath(web) + "/nothing-here");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void unknownServiceIsNotFoundWithAnExplanation() {
        String api = newServiceName("api");

        ResponseEntity<JsonNode> response = get(servicePath(api));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getHeaders().getContentType()).hasToString("application/problem+json");
        assertThat(response.getBody().get("detail").asString()).contains(api);
    }

    @Test
    void malformedBodyIsRefusedWithAnExplanation() {
        String api = newServiceName("api");

        ResponseEntity<JsonNode> response = post(instancesPath(api), "{\"port\": ");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType()).hasToString("application/problem+json");
    }

    @Test
    void servicesWhoseNamesAndHostsRunTogetherKeepTheirOwnInstances() {
        String web = newServiceName("web");

        register(web, Map.of("host", "dev-box", "port", 80));
        register(web + "-dev", Map.of("host", "box", "port", 80));

        assertThat(get(servicePath(web)).getBody().get("instances").get(0).get("host").asString())
                .isEqualTo("dev-box");
        assertThat(get(servicePath(web + "-dev")).getBody().get("instances").get(0).get("host").asString())
                .isEqualTo("box");
    }

    @Test
    void registeringTheSameAddressAgainKeepsOneInstance() {
        String web = newServiceName("web");
        String id = register(web, Map.of("host", "10.1.2.3", "port", 8080)).get("id").asString();

        JsonNode again = register(web, Map.of("host", "10.1.2.3", "port", 8080));

        assertThat(again.get("id").asString()).isEqualTo(id);
        assertThat(get(servicePath(web)).getBody().get("instances")).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = { "10.1.2.3/24", "my host", "dev-box?x", "fe80::1%eth0" })
    void hostThatIsNotAnAddressOrHostnameIsRefused(String host) {
        String api = newServiceName("api");

        ResponseEntity<JsonNode> response = post(instancesPath(api), Map.of("host", host, "port", 8080));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("detail").asString()).contains("not an IP address or a hostname");
    }

    @Test
    void hostDefaultsToTheWorkstationsLanAddress() {
        String api = newServiceName("api");

        register(api, Map.of("port", 8080));

        JsonNode instance = get(servicePath(api)).getBody().get("instances").get(0);
        assertThat(instance.get("host").asString()).isEqualTo(lanAddress()).isNotEqualTo("127.0.0.1");
    }

    @Test
    void instanceWithoutCheckGetsTcpCheckOnItsPort() {
        String redis = newServiceName("redis");
        listenOnLanAddress(6379);

        register(redis, Map.of("port", 6379));

        await().atMost(CHECK_TIMEOUT).untilAsserted(() -> assertThat(
                get(servicePath(redis)).getBody().get("instances").get(0).get("health").asString())
                .isEqualTo("passing"));
    }

    @Test
    void instanceStaysRegisteredWithCriticalHealthWhenNothingListens() {
        String postgres = newServiceName("postgres");

        register(postgres, Map.of("port", 5432));

        JsonNode instance = findService(get("/api/services").getBody(), postgres).get("instances").get(0);
        assertThat(instance.get("port").asInt()).isEqualTo(5432);
        assertThat(instance.get("health").asString()).isEqualTo("critical");
    }

    /** The Service called {@code name} in a list of Services, or {@code null}. */
    static JsonNode findService(JsonNode services, String name) {
        for (JsonNode service : services) {
            if (service.get("name").asString().equals(name)) {
                return service;
            }
        }
        return null;
    }
}
