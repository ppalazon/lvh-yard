package dev.palazon.lvhyard.dashboard;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** The Services of the Yard's other Workstations: seen from here, never changed from here. */
class RemoteServicesTest extends DashboardTest {

    @Test
    void workstationsListsThisWorkstationFirstThenTheOthersWithTheirServices() {
        String web = newServiceName("web");
        register(web, Map.of("host", "10.1.2.3", "port", 8080));
        String api = newServiceName("api");
        remote.register(api, Map.of("host", "10.9.8.7", "port", 9090));

        ResponseEntity<JsonNode> response = get("/api/workstations");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode workstations = response.getBody();
        // ws-remote comes before ws-test by name, but this Workstation is always first.
        assertThat(workstations.valueStream().map(workstation -> workstation.get("name").asString()))
                .containsExactly(WORKSTATION, RemoteWorkstation.NAME);
        assertThat(workstations.get(0).get("unreachable").asBoolean()).isFalse();
        assertThat(serviceNames(workstations.get(0))).contains(web).doesNotContain(api);
        assertThat(workstations.get(1).get("unreachable").asBoolean()).isFalse();
        assertThat(serviceNames(workstations.get(1))).contains(api).doesNotContain(web);
        JsonNode remoteApi = service(workstations.get(1), api);
        assertThat(remoteApi.get("workstation").asString()).isEqualTo(RemoteWorkstation.NAME);
        assertThat(remoteApi.get("instances").get(0).get("host").asString()).isEqualTo("10.9.8.7");
    }

    @Test
    void workstationThatDoesNotAnswerIsUnreachableAndComesBackByItself() {
        String web = newServiceName("web");
        register(web, Map.of("host", "10.1.2.3", "port", 8080));
        String api = newServiceName("api");
        remote.register(api, Map.of("host", "10.9.8.7", "port", 9090));

        remote.stopAnswering();
        try {
            JsonNode workstations = await().atMost(Duration.ofSeconds(60)).until(() -> get("/api/workstations")
                    .getBody(), body -> body.get(1).get("unreachable").asBoolean());

            assertThat(workstations.get(1).get("name").asString()).isEqualTo(RemoteWorkstation.NAME);
            assertThat(workstations.get(1).get("services")).isEmpty();
            assertThat(serviceNames(workstations.get(0))).contains(web);
            ResponseEntity<JsonNode> one = get("/api/services/" + RemoteWorkstation.NAME + "/" + api);
            assertThat(one.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(one.getBody().get("detail").asString()).contains(RemoteWorkstation.NAME + " is unreachable");
            assertThat(get("/api/services?workstation=" + RemoteWorkstation.NAME).getStatusCode())
                    .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(getPage("/services/" + RemoteWorkstation.NAME + "/" + api).getStatusCode())
                    .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            String catalog = getPage("/").getBody();
            assertThat(catalog.substring(catalog.indexOf("<h2>" + RemoteWorkstation.NAME + "</h2>")))
                    .contains("Workstation unreachable").doesNotContain(api);
            assertThat(catalog).contains(web);
            assertThat(get("/api/services").getBody().valueStream().map(service -> service.get("name").asString()))
                    .contains(web).doesNotContain(api);
        } finally {
            remote.answerAgain();
        }

        JsonNode workstations = await().atMost(Duration.ofSeconds(60)).until(() -> get("/api/workstations")
                .getBody(), body -> !body.get(1).get("unreachable").asBoolean());
        assertThat(serviceNames(workstations.get(1))).contains(api);
    }

    @Test
    void remoteServiceCannotBeChangedThroughTheApi() {
        String api = newServiceName("api");
        String id = remote.register(api, Map.of("host", "10.9.8.7", "port", 9090)).get("id").asString();
        String path = "/api/services/" + RemoteWorkstation.NAME + "/" + api;

        List<ResponseEntity<JsonNode>> refusals = List.of(
                post(path + "/instances", Map.of("host", "10.9.8.7", "port", 9091)),
                put(path + "/instances/" + id, Map.of("host", "10.9.8.7", "port", 9092)),
                delete(path + "/instances/" + id),
                put(path + "/publication"),
                delete(path + "/publication"),
                delete(path));

        assertThat(refusals).allSatisfy(refusal -> {
            assertThat(refusal.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
            assertThat(refusal.getHeaders().getAllow()).containsExactly(HttpMethod.GET);
            assertThat(refusal.getBody().get("detail").asString())
                    .contains("only on its Home Workstation").contains("/api/workstation/services");
        });
        JsonNode unchanged = remote.service(api).getBody();
        assertThat(unchanged.get("state").asString()).isEqualTo("unpublished");
        assertThat(unchanged.get("instances").valueStream().map(instance -> instance.get("port").asInt()))
                .containsExactly(9090);
    }

    @Test
    void remoteServicesPageShowsItsInstancesAndHealthWithoutFormsOrControls() {
        String api = newServiceName("api");
        remote.register(api, Map.of("host", "10.9.8.7", "port", 9090));
        remote.publish(api);

        ResponseEntity<String> response = getPage("/services/" + RemoteWorkstation.NAME + "/" + api);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("<h1>" + api + "</h1>").contains("10.9.8.7:9090")
                .contains("critical").contains("Unhealthy")
                .contains("Local Scope: only " + RemoteWorkstation.NAME + " routes it")
                .contains("It can be changed only on " + RemoteWorkstation.NAME);
        String content = response.getBody().substring(response.getBody().indexOf("<main"));
        assertThat(content).doesNotContain("<form").doesNotContain("<button").doesNotContain("/edit\"");
    }

    private static List<String> serviceNames(JsonNode workstation) {
        return workstation.get("services").valueStream().map(service -> service.get("name").asString()).toList();
    }

    private static JsonNode service(JsonNode workstation, String name) {
        return workstation.get("services").valueStream()
                .filter(service -> service.get("name").asString().equals(name)).findFirst().orElseThrow();
    }
}
