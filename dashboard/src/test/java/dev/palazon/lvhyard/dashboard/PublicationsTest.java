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
class PublicationsTest extends DashboardTest {

  /**
   * Long enough for a check's first run, which Consul starts at a random point within its 10 s
   * interval.
   */
  private static final Duration CHECK_TIMEOUT = Duration.ofSeconds(20);

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

    assertThat(get(servicePath(postgres)).getBody().get("state").asString()).isEqualTo("unhealthy");
  }

  @Test
  void publishedServiceWithPassingInstanceIsHealthy() {
    String redis = newServiceName("redis");
    listenOnLanAddress(6380);
    register(redis, Map.of("port", 6380));

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

    JsonNode service = StaticInstancesTest.findService(get("/api/services").getBody(), web);
    assertThat(service.get("state").asString()).isEqualTo("unavailable");
    assertThat(service.get("instances")).isEmpty();
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
  void unpublishingServiceWithoutInstancesRemovesIt() {
    String web = newServiceName("web");
    String id = register(web, Map.of("host", "10.1.2.3", "port", 8080)).get("id").asString();
    publish(web);
    delete(instancesPath(web) + "/" + id);

    delete(publicationPath(web));

    assertThat(get(servicePath(web)).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
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
    assertThat(yard.getBody().get("yardHostname").asString()).isEqualTo(web + ".ws-test.lvh.test");
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
  void publishingYardIsRefused() {
    ResponseEntity<JsonNode> response = put(publicationPath("yard"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().get("detail").asString()).contains("taken by the dashboard");
  }
}
