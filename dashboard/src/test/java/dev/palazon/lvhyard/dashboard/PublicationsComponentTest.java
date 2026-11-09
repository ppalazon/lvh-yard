package dev.palazon.lvhyard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/** Publications of this Workstation's Services, made and removed through the REST API. */
class PublicationsComponentTest extends DashboardComponentTest {

  /**
   * Long enough for a check's first run, which Consul starts at a random point within its 10 s
   * interval.
   */
  private static final Duration CHECK_TIMEOUT = Duration.ofSeconds(20);

  private static boolean accessLog(JsonNode service) {
    return service.get("publication").get("accessLog").asBoolean();
  }

  @Test
  void publishedServiceHasLocalScopeAndItsHostname() {
    String grafana = newServiceName("grafana");
    register(grafana, Map.of("host", "10.1.2.3", "port", 3000));

    ResponseEntity<JsonNode> response = put(publicationPath(grafana));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().get("publication").get("scope").asString()).isEqualTo("local");
    assertThat(response.getBody().get("hostname").asString()).isEqualTo(grafana + ".lvh.test");
    JsonNode service = get(servicePath(grafana)).getBody();
    assertThat(service.get("publication").get("scope").asString()).isEqualTo("local");
    assertThat(service.get("hostname").asString()).isEqualTo(grafana + ".lvh.test");
    assertThat(service.has("yardHostname")).isFalse();
  }

  @Test
  void registeredServiceIsUnpublished() {
    String web = newServiceName("web");

    register(web, Map.of("host", "10.1.2.3", "port", 8080));

    JsonNode service = get(servicePath(web)).getBody();
    assertThat(service.get("state").asString()).isEqualTo("unpublished");
    assertThat(service.has("publication")).isFalse();
    assertThat(service.has("hostname")).isFalse();
  }

  @Test
  void publishedServiceWithoutPassingInstanceIsUnhealthy() {
    String postgres = newServiceName("postgres");
    register(postgres, Map.of("host", "10.1.2.3", "port", 5432));

    publish(postgres);

    JsonNode service = get(servicePath(postgres)).getBody();
    assertThat(service.get("state").asString()).isEqualTo("unhealthy");
    assertThat(service.get("warnings")).isEmpty();
  }

  @Test
  void publishedServiceWithPassingInstanceIsHealthy() {
    String redis = newServiceName("redis");
    int port = newLanPort();
    listenOnLanAddress(port);
    register(redis, Map.of("port", port, "check", Map.of("interval", "1s")));

    publish(redis);

    await()
        .atMost(CHECK_TIMEOUT)
        .untilAsserted(() ->
            assertThat(get(servicePath(redis)).getBody().get("state").asString())
                .isEqualTo("healthy"));
  }

  @Test
  void publishedServiceWithItsLastInstanceRemovedStaysAsUnavailable() {
    String web = newServiceName("web");
    String id = register(web, Map.of("host", "10.1.2.3", "port", 8080)).get("id").asString();
    publish(web);

    delete(instancesPath(web) + "/" + id);

    JsonNode service =
        StaticInstancesComponentTest.findService(get("/api/services").getBody(), web);
    assertThat(service.get("state").asString()).isEqualTo("unavailable");
    assertThat(service.get("instances")).isEmpty();
    assertThat(service.get("warnings")).isEmpty();
  }

  @Test
  void unpublishingKeepsEveryInstance() {
    String web = newServiceName("web");
    register(web, Map.of("host", "10.1.2.3", "port", 8080));
    register(web, Map.of("host", "10.1.2.3", "port", 8081));
    publish(web);

    ResponseEntity<JsonNode> response = delete(publicationPath(web));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    JsonNode service = get(servicePath(web)).getBody();
    assertThat(service.get("state").asString()).isEqualTo("unpublished");
    assertThat(service.get("instances")).hasSize(2);
  }

  @Test
  void unpublishingServiceWithoutInstancesKeepsIt() {
    String web = newServiceName("web");
    String id = register(web, Map.of("host", "10.1.2.3", "port", 8080)).get("id").asString();
    publish(web);
    delete(instancesPath(web) + "/" + id);

    delete(publicationPath(web));

    JsonNode service = get(servicePath(web)).getBody();
    assertThat(service.get("state").asString()).isEqualTo("unpublished");
    assertThat(service.get("empty").asBoolean()).isTrue();
  }

  @Test
  void publishingAgainChangesNothing() {
    String web = newServiceName("web");
    register(web, Map.of("host", "10.1.2.3", "port", 8080));
    publish(web);

    ResponseEntity<JsonNode> response = put(publicationPath(web));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(get(servicePath(web)).getBody().get("publication").get("scope").asString())
        .isEqualTo("local");
  }

  @Test
  void scopeCanBeChangedToYardAndBack() {
    String web = newServiceName("web");
    register(web, Map.of("host", "10.1.2.3", "port", 8080));
    publish(web);

    ResponseEntity<JsonNode> yard = put(publicationPath(web), Map.of("scope", "yard"));

    assertThat(yard.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(yard.getBody().get("publication").get("scope").asString()).isEqualTo("yard");
    assertThat(yard.getBody().get("hostname").asString())
        .as("the Home Workstation keeps the short hostname")
        .isEqualTo(web + ".lvh.test");
    assertThat(yard.getBody().has("yardHostname"))
        .as("each other Workstation names it with its own Base Domain, which this one cannot know")
        .isFalse();
    put(publicationPath(web), Map.of("scope", "local"));
    assertThat(get(servicePath(web)).getBody().get("publication").get("scope").asString())
        .isEqualTo("local");
  }

  @Test
  void newPublicationCanHaveYardScopeAtOnce() {
    String web = newServiceName("web");
    register(web, Map.of("host", "10.1.2.3", "port", 8080));

    put(publicationPath(web), Map.of("scope", "yard"));

    assertThat(get(servicePath(web)).getBody().get("publication").get("scope").asString())
        .isEqualTo("yard");
  }

  @Test
  void publishingAgainWithoutScopeKeepsYardScope() {
    String web = newServiceName("web");
    register(web, Map.of("host", "10.1.2.3", "port", 8080));
    put(publicationPath(web), Map.of("scope", "yard"));

    put(publicationPath(web));

    assertThat(get(servicePath(web)).getBody().get("publication").get("scope").asString())
        .isEqualTo("yard");
  }

  @Test
  void unknownScopeIsRefused() {
    String web = newServiceName("web");
    register(web, Map.of("host", "10.1.2.3", "port", 8080));

    ResponseEntity<JsonNode> response = put(publicationPath(web), Map.of("scope", "world"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(get(servicePath(web)).getBody().get("state").asString()).isEqualTo("unpublished");
  }

  @Test
  void yardScopedServiceWarnsAboutEachInstanceOnLoopback() {
    String web = newServiceName("web");
    register(web, Map.of("host", "127.0.0.1", "port", 8080));
    register(web, Map.of("host", "::1", "port", 8081));
    register(web, Map.of("host", "localhost", "port", 8082));
    register(web, Map.of("host", "10.1.2.3", "port", 8083));

    JsonNode service = put(publicationPath(web), Map.of("scope", "yard")).getBody();

    assertThat(service.get("warnings").valueStream().map(warning -> warning.get("kind").asString()))
        .containsExactly("loopback-instance", "loopback-instance", "loopback-instance");
    assertThat(
            service
                .get("warnings")
                .valueStream()
                .map(warning -> warning.get("severity").asString()))
        .containsExactly("risk", "risk", "risk");
    assertThat(
            service.get("warnings").valueStream().map(warning -> warning.get("message").asString()))
        .anySatisfy(message -> assertThat(message).contains("127.0.0.1:8080"))
        .anySatisfy(message -> assertThat(message).contains("[::1]:8081"))
        .anySatisfy(message -> assertThat(message).contains("localhost:8082"))
        .noneSatisfy(message -> assertThat(message).contains("10.1.2.3"));
  }

  @Test
  void localScopedServiceDoesNotWarnAboutLoopback() {
    String web = newServiceName("web");
    register(web, Map.of("host", "127.0.0.1", "port", 8080));

    JsonNode service = put(publicationPath(web)).getBody();

    assertThat(service.get("warnings")).isEmpty();
  }

  @Test
  void publishingUnknownServiceIsNotFound() {
    String api = newServiceName("api");

    ResponseEntity<JsonNode> response = put(publicationPath(api));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody().get("detail").asString()).contains(api);
    assertThat(get(servicePath(api)).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void unpublishingUnknownServiceIsNotFound() {
    ResponseEntity<JsonNode> response = delete(publicationPath(newServiceName("api")));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void publishingAndUnpublishingLeaveTheInstancePriorityAlone() {
    String web = newServiceName("web");
    register(web, Map.of("host", "10.1.2.3", "port", 8080));
    publish(web);
    put(instancePriorityPath(web), Map.of("on", false));

    put(publicationPath(web), Map.of("scope", "yard"));
    delete(publicationPath(web));
    publish(web);

    assertThat(get(servicePath(web)).getBody().get("instancePriority").asBoolean()).isFalse();
  }

  @Test
  void accessLogIsOffForANewPublicationAndOnIsKeptWhenPublishingAgain() {
    String web = newServiceName("web");
    register(web, Map.of("host", "10.1.2.3", "port", 8080));

    ResponseEntity<JsonNode> published = put(publicationPath(web));

    assertThat(accessLog(published.getBody())).isFalse();

    put(publicationPath(web), Map.of("accessLog", true));
    put(publicationPath(web), Map.of("scope", "yard"));

    assertThat(accessLog(get(servicePath(web)).getBody())).isTrue();
  }

  @Test
  void publicationStoredAsItsScopeAloneIsRead() {
    String api = newServiceName("api");
    remote.register(api, Map.of("host", "10.9.8.7", "port", 9090));
    remote.putConsulValue("lvh-yard/publications/" + api, "yard");

    JsonNode service = get("/api/services/" + RemoteWorkstation.NAME + "/" + api).getBody();

    assertThat(service.get("publication").get("scope").asString()).isEqualTo("yard");
    assertThat(service.get("instancePriority").asBoolean()).isTrue();
  }
}
