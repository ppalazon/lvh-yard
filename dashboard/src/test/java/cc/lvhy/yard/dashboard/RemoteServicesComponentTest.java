package cc.lvhy.yard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/** The Services of the Yard's other Workstations: seen from here, never changed from here. */
class RemoteServicesComponentTest extends DashboardComponentTest {

  /** The {@link RemoteWorkstation} among the Yard's remotes. */
  static JsonNode remoteOf(JsonNode yard) {
    return yard.get("remotes")
        .valueStream()
        .filter(workstation -> workstation.get("name").asString().equals(RemoteWorkstation.NAME))
        .findFirst()
        .orElseThrow();
  }

  static List<String> serviceNames(JsonNode workstation) {
    return workstation
        .get("services")
        .valueStream()
        .map(service -> service.get("name").asString())
        .toList();
  }

  private static JsonNode service(JsonNode workstation, String name) {
    return workstation
        .get("services")
        .valueStream()
        .filter(service -> service.get("name").asString().equals(name))
        .findFirst()
        .orElseThrow();
  }

  @Test
  void workstationNotInTheYardIsNotFound() {
    ResponseEntity<JsonNode> services = get("/api/services?workstation=ws-typo");
    ResponseEntity<JsonNode> service = get("/api/services/ws-typo/" + newServiceName("api"));

    assertThat(services.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(services.getBody().get("detail").asString())
        .contains("The Yard has no Workstation ws-typo");
    assertThat(service.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(service.getBody().get("detail").asString())
        .contains("The Yard has no Workstation ws-typo");
    assertThat(getPage("/services/ws-typo/web").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void unknownServiceOfAReachableWorkstationIsNotFound() {
    ResponseEntity<JsonNode> response =
        get("/api/services/" + RemoteWorkstation.NAME + "/" + newServiceName("missing"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void yardShowsThisWorkstationAsHomeAndTheOthersAsRemotesWithTheirServices() {
    String web = newServiceName("web");
    register(web, Map.of("host", "10.1.2.3", "port", 8080));
    String api = newServiceName("api");
    remote.register(api, Map.of("host", "10.9.8.7", "port", 9090));

    ResponseEntity<JsonNode> response = get("/api/yard");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode home = response.getBody().get("home");
    assertThat(home.get("name").asString()).isEqualTo(WORKSTATION);
    assertThat(home.has("unreachable")).as("this Workstation always answers").isFalse();
    assertThat(serviceNames(home)).contains(web).doesNotContain(api);
    JsonNode remotes = response.getBody().get("remotes");
    assertThat(remotes.valueStream().map(workstation -> workstation.get("name").asString()))
        .containsExactly(RemoteWorkstation.NAME);
    assertThat(remotes.get(0).get("unreachable").asBoolean()).isFalse();
    assertThat(serviceNames(remotes.get(0))).contains(api).doesNotContain(web);
    JsonNode remoteApi = service(remotes.get(0), api);
    assertThat(remoteApi.get("workstation").asString()).isEqualTo(RemoteWorkstation.NAME);
    assertThat(remoteApi.get("instances").get(0).get("host").asString()).isEqualTo("10.9.8.7");
  }

  @Test
  void remoteServiceCannotBeChangedThroughTheApi() {
    String api = newServiceName("api");
    String id = remote.register(api, Map.of("host", "10.9.8.7", "port", 9090)).get("id").asString();
    String path = "/api/services/" + RemoteWorkstation.NAME + "/" + api;

    List<ResponseEntity<JsonNode>> refusals =
        List.of(
            post(path + "/instances", Map.of("host", "10.9.8.7", "port", 9091)),
            put(path + "/instances/" + id, Map.of("host", "10.9.8.7", "port", 9092)),
            delete(path + "/instances/" + id),
            put(path + "/publication"),
            delete(path + "/publication"),
            delete(path));

    assertThat(refusals)
        .allSatisfy(refusal -> {
          assertThat(refusal.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
          assertThat(refusal.getHeaders().getAllow()).containsExactly(HttpMethod.GET);
          assertThat(refusal.getBody().get("detail").asString())
              .contains("only on its Home Workstation")
              .contains("/api/workstation/services");
        });
    JsonNode unchanged = remote.service(api).getBody();
    assertThat(unchanged.get("state").asString()).isEqualTo("unpublished");
    assertThat(
            unchanged.get("instances").valueStream().map(instance -> instance.get("port").asInt()))
        .containsExactly(9090);
  }

  @Test
  void remoteServiceWithYardScopeHasItsWorkstationsHostnameAndWithLocalScopeNone() {
    String api = newServiceName("api");
    remote.register(api, Map.of("host", "10.9.8.7", "port", 9090));

    remote.publish(api, "yard");

    JsonNode service = get("/api/services/" + RemoteWorkstation.NAME + "/" + api).getBody();
    assertThat(service.get("publication").get("scope").asString()).isEqualTo("yard");
    assertThat(service.get("hostname").asString())
        .isEqualTo(api + "." + RemoteWorkstation.NAME + ".lvh.test");

    remote.publish(api, "local");

    assertThat(get("/api/services/" + RemoteWorkstation.NAME + "/" + api).getBody().has("hostname"))
        .as("Local Scope has no hostname here")
        .isFalse();
  }

  @Test
  void remoteInstanceOnLoopbackOrADockerNetworkIsOutOfRange() {
    String api = newServiceName("api");
    for (String host : List.of("10.9.8.7", "127.0.0.1", "172.17.0.2")) {
      remote.register(api, Map.of("host", host, "port", 9090));
    }

    JsonNode instances =
        get("/api/services/" + RemoteWorkstation.NAME + "/" + api).getBody().get("instances");

    assertThat(
            instances
                .valueStream()
                .map(instance ->
                    instance.get("host").asString() + " " + instance.get("outOfRange").asBoolean()))
        .containsExactlyInAnyOrder("10.9.8.7 false", "127.0.0.1 true", "172.17.0.2 true");
    assertThat(
            remote
                .service(api)
                .getBody()
                .get("instances")
                .valueStream()
                .map(instance -> instance.get("outOfRange").asBoolean()))
        .as("its Home Workstation reaches it")
        .containsOnly(false);
  }

  @Test
  void remoteServicesPageMarksAnInstanceOutOfRange() {
    String api = newServiceName("api");
    remote.register(api, Map.of("host", "127.0.0.1", "port", 9090));

    String body = getPage("/services/" + RemoteWorkstation.NAME + "/" + api).getBody();

    assertThat(body).contains("127.0.0.1:9090").contains("Out of range");
  }

  @Test
  void remoteServicesPageShowsItsInstancesAndHealth() {
    String api = newServiceName("api");
    remote.register(api, Map.of("host", "10.9.8.7", "port", 9090));
    remote.publish(api);

    ResponseEntity<String> response = getPage("/services/" + RemoteWorkstation.NAME + "/" + api);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody())
        .contains("<h1>" + api + "</h1>")
        .contains("10.9.8.7:9090")
        .contains("critical")
        .contains("Unhealthy")
        .contains("Local Scope: only " + RemoteWorkstation.NAME + " routes it")
        .contains("Home Workstation: " + RemoteWorkstation.NAME + ". Change it only there.");
  }
}
