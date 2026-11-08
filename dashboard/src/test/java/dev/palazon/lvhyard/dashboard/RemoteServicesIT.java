package dev.palazon.lvhyard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/** The Services of the Yard's other Workstations: seen from here, never changed from here. */
class RemoteServicesIT extends DashboardIT {

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
  void workstationThatDoesNotAnswerIsUnreachableAndComesBackByItself() {
    String web = newServiceName("web");
    register(web, Map.of("host", "10.1.2.3", "port", 8080));
    String api = newServiceName("api");
    remote.register(api, Map.of("host", "10.9.8.7", "port", 9090));
    remote.publish(api, "yard");
    publish(web);
    // The Alias makes this Workstation's own Service read the Remote Service as its fallback.
    put(AliasesIT.aliasPath(web), Map.of("workstation", RemoteWorkstation.NAME, "service", api));

    remote.stopAnswering();
    try {
      JsonNode yard =
          await()
              .atMost(Duration.ofSeconds(60))
              .until(
                  () -> get("/api/yard").getBody(),
                  body -> remoteOf(body).get("unreachable").asBoolean());

      assertThat(remoteOf(yard).get("services")).isEmpty();
      // A Workstation that keeps failing is not asked again for a while, so the Yard answers
      // without waiting.
      await()
          .atMost(Duration.ofSeconds(30))
          .until(() -> answerTime("/api/yard"), time -> time.compareTo(Duration.ofSeconds(1)) < 0);
      for (String own : List.of("/api/workstation/services", servicePath(web))) {
        await()
            .atMost(Duration.ofSeconds(30))
            .until(() -> answerTime(own), time -> time.compareTo(Duration.ofSeconds(1)) < 0);
      }
      assertThat(serviceNames(yard.get("home"))).contains(web);
      ResponseEntity<JsonNode> one = get("/api/services/" + RemoteWorkstation.NAME + "/" + api);
      assertThat(one.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
      assertThat(one.getBody().get("detail").asString())
          .contains(RemoteWorkstation.NAME + " is unreachable");
      assertThat(get("/api/services?workstation=" + RemoteWorkstation.NAME).getStatusCode())
          .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
      assertThat(getPage("/services/" + RemoteWorkstation.NAME + "/" + api).getStatusCode())
          .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
      String catalog = getPage("/").getBody();
      assertThat(catalog.substring(catalog.indexOf("<h2>" + RemoteWorkstation.NAME + "</h2>")))
          .contains("Workstation unreachable")
          .doesNotContain(api);
      assertThat(catalog).contains(web);
      assertThat(
              get("/api/services")
                  .getBody()
                  .valueStream()
                  .map(service -> service.get("name").asString()))
          .contains(web)
          .doesNotContain(api);
    } finally {
      remote.answerAgain();
    }

    JsonNode yard =
        await()
            .atMost(Duration.ofSeconds(60))
            .until(
                () -> get("/api/yard").getBody(),
                body -> !remoteOf(body).get("unreachable").asBoolean());
    assertThat(serviceNames(remoteOf(yard))).contains(api);
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
  void remoteServicesPageShowsItsInstancesAndHealthWithoutFormsOrControls() {
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
        .contains("It can be changed only on " + RemoteWorkstation.NAME);
    String content = response.getBody().substring(response.getBody().indexOf("<main"));
    // Its one form is the Reachability test's, which only reads.
    assertThat(content).doesNotContain("method=\"post\"").doesNotContain("/edit\"");
    assertThat(content.split("<form", -1)).hasSize(2);
    assertThat(content)
        .contains("action=\"/services/" + RemoteWorkstation.NAME + "/" + api + "/reachability\"");
  }

  @Test
  void remoteServiceWithYardScopeHasItsWorkstationsHostname() {
    String api = newServiceName("api");
    remote.register(api, Map.of("host", "10.9.8.7", "port", 9090));

    remote.publish(api, "yard");

    JsonNode service = get("/api/services/" + RemoteWorkstation.NAME + "/" + api).getBody();
    assertThat(service.get("publication").get("scope").asString()).isEqualTo("yard");
    assertThat(service.get("hostname").asString())
        .isEqualTo(api + "." + RemoteWorkstation.NAME + ".lvh.test");
  }

  @Test
  void remoteServiceWithLocalScopeHasNoHostnameHere() {
    String api = newServiceName("api");
    remote.register(api, Map.of("host", "10.9.8.7", "port", 9090));

    remote.publish(api, "local");

    assertThat(get("/api/services/" + RemoteWorkstation.NAME + "/" + api).getBody().has("hostname"))
        .isFalse();
  }

  @Test
  void remoteInstanceOnLoopbackOrADockerNetworkIsOutOfRange() {
    String api = newServiceName("api");
    for (String host :
        List.of(
            "10.9.8.7",
            "127.0.0.1",
            "::1",
            "localhost",
            "172.17.0.2",
            "172.18.0.5",
            "172.31.255.254",
            "172.16.0.9",
            "172.32.0.1",
            "192.168.1.20")) {
      remote.register(api, Map.of("host", host, "port", 9090));
    }

    JsonNode instances =
        get("/api/services/" + RemoteWorkstation.NAME + "/" + api).getBody().get("instances");

    assertThat(
            instances
                .valueStream()
                .map(instance ->
                    instance.get("host").asString() + " " + instance.get("outOfRange").asBoolean()))
        .containsExactlyInAnyOrder(
            "10.9.8.7 false",
            "127.0.0.1 true",
            "::1 true",
            "localhost true",
            "172.17.0.2 true",
            "172.18.0.5 true",
            "172.31.255.254 true",
            "172.16.0.9 false",
            "172.32.0.1 false",
            "192.168.1.20 false");
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

  /** How long the dashboard takes to answer a GET of {@code path}. */
  private Duration answerTime(String path) {
    long start = System.nanoTime();
    get(path);
    return Duration.ofNanos(System.nanoTime() - start);
  }

  /** The {@link RemoteWorkstation} among the Yard's remotes. */
  private static JsonNode remoteOf(JsonNode yard) {
    return yard.get("remotes")
        .valueStream()
        .filter(workstation -> workstation.get("name").asString().equals(RemoteWorkstation.NAME))
        .findFirst()
        .orElseThrow();
  }

  private static List<String> serviceNames(JsonNode workstation) {
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
}
