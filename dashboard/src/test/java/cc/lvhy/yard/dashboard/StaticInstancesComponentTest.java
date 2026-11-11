package cc.lvhy.yard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/** Static Instances, registered and removed through the REST API. */
class StaticInstancesComponentTest extends DashboardComponentTest {

  /**
   * Long enough for a check's first run, which Consul starts at a random point within its 10 s
   * interval.
   */
  private static final Duration CHECK_TIMEOUT = Duration.ofSeconds(20);

  /** The Service called {@code name} in a list of Services, or {@code null}. */
  static JsonNode findService(JsonNode services, String name) {
    for (JsonNode service : services) {
      if (service.get("name").asString().equals(name)) {
        return service;
      }
    }
    return null;
  }

  @Test
  void registeredInstanceIsListedWithItsService() {
    String grafana = newServiceName("grafana");
    create(grafana);

    ResponseEntity<JsonNode> response =
        post(instancesPath(grafana), Map.of("host", "10.1.2.3", "port", 3000));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    JsonNode service = findService(get("/api/services").getBody(), grafana);
    assertThat(service.get("workstation").asString()).isEqualTo(WORKSTATION);
    JsonNode instance = service.get("instances").get(0);
    assertThat(instance.get("host").asString()).isEqualTo("10.1.2.3");
    assertThat(instance.get("port").asInt()).isEqualTo(3000);
  }

  @Test
  void registeringIntoAnUnpublishedServiceKeepsItUnpublished() {
    String web = newServiceName("web");
    create(web);

    ResponseEntity<JsonNode> response = post(instancesPath(web), Map.of("port", 8080));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(get(servicePath(web)).getBody().has("publication")).isFalse();
  }

  @Test
  void registeringIntoAPublishedServiceKeepsItsScope() {
    String web = newServiceName("web");
    register(web, Map.of("port", 8080));
    put(publicationPath(web), Map.of("scope", "yard"));

    post(instancesPath(web), Map.of("port", 8081));

    JsonNode service = get(servicePath(web)).getBody();
    assertThat(service.get("publication").get("scope").asString()).isEqualTo("yard");
  }

  @Test
  void refusedRegistrationPublishesNothing() {
    String web = newServiceName("web");

    post(instancesPath(web), Map.of("port", 0));

    assertThat(get(servicePath(web)).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void serviceCanHaveMoreThanOneInstance() {
    String web = newServiceName("web");

    register(web, Map.of("host", "10.1.2.3", "port", 8080));
    register(web, Map.of("host", "10.1.2.3", "port", 8081));

    JsonNode instances = get(servicePath(web)).getBody().get("instances");
    assertThat(instances.valueStream().map(instance -> instance.get("port").asInt()))
        .containsExactly(8080, 8081);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "Grafana",
        "my_api",
        "api.v2",
        "-api",
        "api-",
        "a-label-of-sixty-four-characters-is-one-too-long-for-the-dns-xyz"
      })
  void serviceNameThatIsNotOneDnsLabelIsRefused(String name) {
    ResponseEntity<JsonNode> response = post(instancesPath(name), Map.of("port", 8080));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().get("detail").asString()).contains("single DNS label");
    assertThat(findService(get("/api/services").getBody(), name)).isNull();
  }

  @Test
  void serviceNameConsulIsRefusedWithTheReason() {
    ResponseEntity<JsonNode> response = post(instancesPath("consul"), Map.of("port", 8080));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().get("detail").asString())
        .isEqualTo("consul is Consul's own entry in its catalog");
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 65536})
  void portOutsideTheTcpRangeIsRefused(int port) {
    String api = newServiceName("api");

    ResponseEntity<JsonNode> response = post(instancesPath(api), Map.of("port", port));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().get("detail").asString()).contains("1 to 65535");
  }

  @Test
  void ownServicesAreListedWithoutNamingTheWorkstation() {
    String grafana = newServiceName("grafana");

    register(grafana, Map.of("host", "10.1.2.3", "port", 3000));

    JsonNode service = findService(get("/api/workstation/services").getBody(), grafana);
    assertThat(service.get("workstation").asString()).isEqualTo(WORKSTATION);
    assertThat(service.get("instances").get(0).get("port").asInt()).isEqualTo(3000);
  }

  @Test
  void yardListCanBeFilteredByWorkstation() {
    String grafana = newServiceName("grafana");

    register(grafana, Map.of("host", "10.1.2.3", "port", 3000));

    assertThat(findService(get("/api/services?workstation=" + WORKSTATION).getBody(), grafana))
        .isNotNull();
    assertThat(
            findService(
                get("/api/services?workstation=" + RemoteWorkstation.NAME).getBody(), grafana))
        .isNull();
  }

  @Test
  void ownServiceIsReadInTheYardUnderItsWorkstation() {
    String grafana = newServiceName("grafana");

    register(grafana, Map.of("host", "10.1.2.3", "port", 3000));

    ResponseEntity<JsonNode> response = get("/api/services/" + WORKSTATION + "/" + grafana);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().get("instances").get(0).get("host").asString())
        .isEqualTo("10.1.2.3");
  }

  @Test
  void unknownServiceInTheYardIsNotFound() {
    String grafana = newServiceName("grafana");

    ResponseEntity<JsonNode> response =
        get("/api/services/" + RemoteWorkstation.NAME + "/" + grafana);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody().get("detail").asString())
        .contains(RemoteWorkstation.NAME)
        .contains(grafana);
  }

  @Test
  void removedInstanceIsGoneAndItsServiceKeepsTheOthers() {
    String web = newServiceName("web");
    String first = register(web, Map.of("host", "10.1.2.3", "port", 8080)).get("id").asString();
    register(web, Map.of("host", "10.1.2.3", "port", 8081));

    ResponseEntity<JsonNode> response = delete(instancesPath(web) + "/" + first);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    JsonNode instances = get(servicePath(web)).getBody().get("instances");
    assertThat(instances.valueStream().map(instance -> instance.get("port").asInt()))
        .containsExactly(8081);
  }

  @Test
  void serviceWithItsLastInstanceRemovedStaysEmpty() {
    String web = newServiceName("web");
    String id = register(web, Map.of("port", 8080)).get("id").asString();

    delete(instancesPath(web) + "/" + id);

    JsonNode service = findService(get("/api/services").getBody(), web);
    assertThat(service.get("instances")).isEmpty();
    assertThat(service.get("empty").asBoolean()).isTrue();
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
    assertThat(
            get(servicePath(web + "-dev")).getBody().get("instances").get(0).get("host").asString())
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
  @ValueSource(strings = {"10.1.2.3/24", "my host", "dev-box?x", "fe80::1%eth0"})
  void hostThatIsNotAnAddressOrHostnameIsRefused(String host) {
    String api = newServiceName("api");

    ResponseEntity<JsonNode> response =
        post(instancesPath(api), Map.of("host", host, "port", 8080));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().get("detail").asString())
        .contains("not an IP address or a hostname");
  }

  @Test
  void hostDefaultsToTheWorkstationsLanAddress() {
    String api = newServiceName("api");

    register(api, Map.of("port", 8080));

    JsonNode instance = get(servicePath(api)).getBody().get("instances").get(0);
    assertThat(instance.get("host").asString()).isEqualTo(lanAddress()).isNotEqualTo("127.0.0.1");
  }

  @Test
  void instanceStaysRegisteredWithCriticalHealthWhenNothingListens() {
    String postgres = newServiceName("postgres");

    register(postgres, Map.of("port", 5432));

    JsonNode instance =
        findService(get("/api/services").getBody(), postgres).get("instances").get(0);
    assertThat(instance.get("port").asInt()).isEqualTo(5432);
    assertThat(instance.get("health").asString()).isEqualTo("critical");
  }

  @Test
  void instanceKeepsItsTagsAndMeta() {
    String api = newServiceName("api");

    register(
        api,
        Map.of("port", 8080, "tags", List.of("backend", "v2"), "meta", Map.of("owner", "pablo")));

    JsonNode instance = get(servicePath(api)).getBody().get("instances").get(0);
    assertThat(instance.get("tags").valueStream().map(JsonNode::asString))
        .containsExactly("backend", "v2");
    assertThat(instance.get("meta").properties())
        .singleElement()
        .satisfies(entry ->
            assertThat(entry.getKey() + "=" + entry.getValue().asString())
                .isEqualTo("owner=pablo"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"lvh-yard-instance", "consul-version", "my key", "a.b"})
  void metaKeyThatIsReservedOrHasOtherCharactersIsRefused(String key) {
    String api = newServiceName("api");

    ResponseEntity<JsonNode> response =
        post(instancesPath(api), Map.of("port", 8080, "meta", Map.of(key, "x")));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().get("detail").asString()).contains(key);
    assertThat(get(servicePath(api)).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @ParameterizedTest
  @ValueSource(strings = {"two words", "a,b", ""})
  void tagWithASpaceOrACommaIsRefused(String tag) {
    ResponseEntity<JsonNode> response =
        post(instancesPath(newServiceName("api")), Map.of("port", 8080, "tags", List.of(tag)));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().get("detail").asString()).contains("tag");
  }

  @Test
  void editingAnInstanceKeepsItTheSameInstance() {
    String api = newServiceName("api");
    String id =
        register(api, Map.of("host", "10.1.2.3", "port", 8080, "tags", List.of("old")))
            .get("id")
            .asString();

    ResponseEntity<JsonNode> response =
        put(
            instancesPath(api) + "/" + id,
            Map.of(
                "host",
                "10.1.2.4",
                "port",
                9090,
                "check",
                Map.of("path", "/health", "interval", "5s"),
                "tags",
                List.of("new"),
                "meta",
                Map.of("owner", "pablo")));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().get("id").asString()).isEqualTo(id);
    JsonNode instances = get(servicePath(api)).getBody().get("instances");
    assertThat(instances).hasSize(1);
    JsonNode instance = instances.get(0);
    assertThat(instance.get("id").asString()).isEqualTo(id);
    assertThat(instance.get("host").asString()).isEqualTo("10.1.2.4");
    assertThat(instance.get("port").asInt()).isEqualTo(9090);
    assertThat(instance.get("check").get("path").asString()).isEqualTo("/health");
    assertThat(instance.get("check").get("interval").asString()).isEqualTo("5s");
    assertThat(instance.get("tags").valueStream().map(JsonNode::asString)).containsExactly("new");
    assertThat(instance.get("meta").get("owner").asString()).isEqualTo("pablo");
  }

  @Test
  void editingAnInstanceWithBrokenRulesLeavesItAsItWas() {
    String api = newServiceName("api");
    String id = register(api, Map.of("host", "10.1.2.3", "port", 8080)).get("id").asString();

    ResponseEntity<JsonNode> response = put(instancesPath(api) + "/" + id, Map.of("port", 70000));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(get(servicePath(api)).getBody().get("instances").get(0).get("port").asInt())
        .isEqualTo(8080);
  }

  @Test
  void editingAnInstanceOntoAnotherInstancesAddressIsRefused() {
    String web = newServiceName("web");
    String first = register(web, Map.of("host", "10.1.2.3", "port", 8080)).get("id").asString();
    register(web, Map.of("host", "10.1.2.3", "port", 8081));

    ResponseEntity<JsonNode> response =
        put(instancesPath(web) + "/" + first, Map.of("host", "10.1.2.3", "port", 8081));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().get("detail").asString()).contains("10.1.2.3:8081");
    assertThat(get(servicePath(web)).getBody().get("instances")).hasSize(2);
  }

  @Test
  void editingAnUnknownInstanceIsNotFound() {
    String web = newServiceName("web");
    register(web, Map.of("port", 8080));

    ResponseEntity<JsonNode> response =
        put(instancesPath(web) + "/nothing-here", Map.of("port", 8081));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(get(servicePath(web)).getBody().get("instances")).hasSize(1);
  }
}
