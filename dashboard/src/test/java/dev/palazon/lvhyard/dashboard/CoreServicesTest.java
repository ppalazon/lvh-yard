package dev.palazon.lvhyard.dashboard;

import static dev.palazon.lvhyard.dashboard.TraefikConfigurationTest.routedHostnames;
import static dev.palazon.lvhyard.dashboard.TraefikConfigurationTest.serverUrlsAt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/** The dashboard, Consul and Traefik, which every Workstation has as its Core Services. */
class CoreServicesTest extends DashboardTest {

  @Test
  void dashboardIsACoreServiceWithAHealthCheckAndLocalScope() {
    ResponseEntity<JsonNode> response = get(servicePath("yard"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode yard = response.getBody();
    assertThat(yard.get("core").asBoolean()).isTrue();
    assertThat(yard.get("publication").get("scope").asString()).isEqualTo("local");
    assertThat(yard.get("hostname").asString()).isEqualTo("yard.lvh.test");
    assertThat(yard.get("instances")).hasSize(1);
    JsonNode instance = yard.get("instances").get(0);
    assertThat(instance.get("core").asBoolean()).isTrue();
    assertThat(instance.get("host").asString()).isEqualTo("127.0.0.1");
    assertThat(instance.get("port").asInt()).isEqualTo(port);
    assertCheck(instance, "/actuator/health");
  }

  @Test
  void consulAndTraefikAreCoreServicesOnLoopbackWithHealthChecksAndLocalScope() {
    JsonNode consul = get(servicePath("consul")).getBody();
    JsonNode traefik = get(servicePath("traefik")).getBody();

    assertThat(consul.get("core").asBoolean()).isTrue();
    assertThat(consul.get("hostname").asString()).isEqualTo("consul.lvh.test");
    assertThat(consul.get("publication").get("scope").asString()).isEqualTo("local");
    assertThat(consul.get("instances")).as("not the Consul server's own entry").hasSize(1);
    JsonNode consulInstance = consul.get("instances").get(0);
    assertThat(consulInstance.get("host").asString()).isEqualTo("127.0.0.1");
    assertThat(consulInstance.get("port").asInt())
        .isEqualTo(consulContainer.getMappedPort(TestcontainersConfiguration.CONSUL_HTTP_PORT));
    assertCheck(consulInstance, "/v1/status/leader");

    assertThat(traefik.get("core").asBoolean()).isTrue();
    assertThat(traefik.get("hostname").asString()).isEqualTo("traefik.lvh.test");
    assertThat(traefik.get("publication").get("scope").asString()).isEqualTo("local");
    JsonNode traefikInstance = traefik.get("instances").get(0);
    assertThat(traefikInstance.get("host").asString()).isEqualTo("127.0.0.1");
    assertThat(traefikInstance.get("port").asInt())
        .as("YARD_TRAEFIK_ADMIN_PORT")
        .isEqualTo(TestcontainersConfiguration.TRAEFIK_ADMIN_PORT);
    assertCheck(traefikInstance, "/ping");
  }

  @Test
  void otherServicesAreNotCore() {
    String web = newServiceName("web");
    register(web, Map.of("host", "10.1.2.3", "port", 8080));

    JsonNode service = get(servicePath(web)).getBody();

    assertThat(service.has("core")).isFalse();
    assertThat(service.get("instances").get(0).has("core")).isFalse();
  }

  @Test
  void startingAgainLeavesTheCoreServicesAsTheyAre() {
    String consulId = coreInstanceId("consul");
    String traefikId = coreInstanceId("traefik");

    remote.whileDashboardIsStopped(() -> {});

    for (String service : new String[] {"consul", "traefik"}) {
      JsonNode after = remote.service(service).getBody();
      assertThat(after.get("instances")).as("no second Instance of " + service).hasSize(1);
      assertThat(after.get("publication").get("scope").asString()).isEqualTo("local");
    }
    assertThat(coreInstanceId("consul")).isEqualTo(consulId);
    assertThat(coreInstanceId("traefik")).isEqualTo(traefikId);
  }

  @Test
  void startingAgainPutsBackWhatWasRemoved() {
    String consulId = coreInstanceId("consul");

    remote.whileDashboardIsStopped(() -> {
      remote.deregisterInConsul(consulId);
      remote.deleteConsulValue("lvh-yard/publications/traefik");
    });

    JsonNode consul = remote.service("consul").getBody();
    assertThat(consul.get("instances")).hasSize(1);
    assertThat(consul.get("instances").get(0).get("id").asString()).isEqualTo(consulId);
    assertThat(consul.get("instances").get(0).get("port").asInt()).isEqualTo(remote.consulPort());
    assertCheck(consul.get("instances").get(0), "/v1/status/leader");
    assertThat(remote.service("traefik").getBody().get("publication").get("scope").asString())
        .isEqualTo("local");
  }

  @Test
  void startingAgainPutsBackACheckThatWasRemoved() {
    String consulId = coreInstanceId("consul");
    remote.whileDashboardIsStopped(() -> remote.deregisterCheckInConsul(consulId));

    JsonNode consul = remote.service("consul").getBody();
    assertThat(consul.get("instances")).hasSize(1);
    assertThat(consul.get("instances").get(0).get("id").asString()).isEqualTo(consulId);
    assertCheck(consul.get("instances").get(0), "/v1/status/leader");
  }

  @Test
  void startingAgainGivesACoreServiceWithYardScopeLocalScope() {
    remote.whileDashboardIsStopped(
        () -> remote.putConsulValue("lvh-yard/publications/consul", "yard"));

    assertThat(remote.service("consul").getBody().get("publication").get("scope").asString())
        .isEqualTo("local");
  }

  @Test
  void coreInstancesFollowTheSettings() throws IOException {
    int adminPort;
    try (ServerSocket free = new ServerSocket(0)) {
      adminPort = free.getLocalPort();
    }
    try {
      remote.whileDashboardIsStopped(
          () -> {}, "--server.address=0.0.0.0", "--lvh-yard.traefik.admin-port=" + adminPort);

      JsonNode yard = remote.service("yard").getBody().get("instances");
      assertThat(yard).as("the old port's Instance is replaced").hasSize(1);
      assertThat(yard.get(0).get("host").asString()).isEqualTo("127.0.0.1");
      assertThat(yard.get(0).get("port").asInt()).isEqualTo(remote.dashboardPort());
      JsonNode traefik = remote.service("traefik").getBody().get("instances");
      assertThat(traefik).hasSize(1);
      assertThat(traefik.get(0).get("port").asInt()).isEqualTo(adminPort);
    } finally {
      remote.whileDashboardIsStopped(() -> {});
    }
  }

  @Test
  void startingWritesThatTheCoreServicesAreKept() {
    Instant beforeStart = Instant.now();

    remote.whileDashboardIsStopped(() -> {});

    String initialized = remote.consulValue(RemoteWorkstation.CORE_SERVICES_INITIALIZED);
    assertThat(initialized).isNotNull();
    assertThat(Instant.parse(initialized)).isAfterOrEqualTo(beforeStart);
  }

  @Test
  void consulThatDoesNotAnswerAtStartIsTriedAgainWithoutHoldingUpTheStart() {
    String consulId = coreInstanceId("consul");
    Instant[] starting = new Instant[1];

    Duration start;
    try {
      remote.whileDashboardIsStopped(() -> {
        remote.deregisterInConsul(consulId);
        remote.stopAnswering();
        starting[0] = Instant.now();
      });
      start = Duration.between(starting[0], Instant.now());
    } finally {
      remote.answerAgain();
    }

    // A Consul call that gets no answer waits 3 s; the start must not wait for one.
    assertThat(start).isLessThan(Duration.ofSeconds(3));

    JsonNode consul =
        await()
            .atMost(Duration.ofSeconds(30))
            .ignoreExceptions()
            .until(
                () -> remote.service("consul").getBody(),
                service -> service.get("instances").size() == 1);
    assertThat(consul.get("instances").get(0).get("id").asString()).isEqualTo(consulId);
  }

  @Test
  void coreServicesAreRoutedAtTheirHostnames() {
    JsonNode http = get("/traefik/configuration").getBody().get("http");

    assertThat(serverUrlsAt(http, "yard.lvh.test")).containsExactly("http://127.0.0.1:" + port);
    assertThat(serverUrlsAt(http, "consul.lvh.test"))
        .containsExactly(
            "http://127.0.0.1:"
                + consulContainer.getMappedPort(TestcontainersConfiguration.CONSUL_HTTP_PORT));
    assertThat(serverUrlsAt(http, "traefik.lvh.test"))
        .containsExactly("http://127.0.0.1:" + TestcontainersConfiguration.TRAEFIK_ADMIN_PORT);
  }

  @Test
  void dashboardHostnameIsRoutedWhileTheYardCoreServiceIsMissing() {
    String yardId = coreInstanceId("yard");
    try {
      remote.deregisterInConsul(yardId);
      remote.deleteConsulValue("lvh-yard/publications/yard");

      JsonNode http = remote.traefikConfiguration().get("http");

      assertThat(serverUrlsAt(http, "yard.lvh.test"))
          .containsExactly("http://127.0.0.1:" + remote.dashboardPort());
    } finally {
      remote.whileDashboardIsStopped(() -> {});
    }
  }

  @Test
  void teammatesCoreServicesAreShownMarkedWithoutARoute() {
    List<String> routed = routedHostnames(get("/traefik/configuration").getBody().get("http"));
    for (String name : new String[] {"yard", "consul", "traefik"}) {
      JsonNode service = get("/api/services/" + RemoteWorkstation.NAME + "/" + name).getBody();

      assertThat(service.get("core").asBoolean()).as(name).isTrue();
      assertThat(service.get("instances")).as(name).hasSize(1);
      assertThat(service.get("instances").get(0).get("core").asBoolean()).isTrue();
      assertThat(service.has("hostname")).as(name + " has Local Scope").isFalse();
      assertThat(routed).doesNotContain(name + "." + RemoteWorkstation.NAME + ".lvh.test");
    }
  }

  @Test
  void coreServiceWhoseInstanceIsMissingIsStillMarkedWithItsControlsHidden() {
    String consulId = coreInstanceId("consul");
    try {
      remote.deregisterInConsul(consulId);

      JsonNode consul = remote.service("consul").getBody();
      assertThat(consul.get("instances")).isEmpty();
      assertThat(consul.get("core").asBoolean()).isTrue();
      String page = remote.page("/services/" + RemoteWorkstation.NAME + "/consul");
      assertThat(page.substring(page.indexOf("<main")))
          .contains("Core Service")
          .doesNotContain("Unpublish")
          .doesNotContain("Share with the Yard");
    } finally {
      remote.whileDashboardIsStopped(() -> {});
    }
  }

  @Test
  void teammateWithoutCoreServicesShowsNoConsulService() {
    String consulId = coreInstanceId("consul");
    try {
      // As a teammate on a version from before Core Services: only Consul's own entry is there.
      remote.deregisterInConsul(consulId);
      remote.deleteConsulValue("lvh-yard/publications/consul");

      assertThat(get("/api/services/" + RemoteWorkstation.NAME + "/consul").getStatusCode())
          .isEqualTo(HttpStatus.NOT_FOUND);
      for (JsonNode service :
          get("/api/services?workstation=" + RemoteWorkstation.NAME).getBody()) {
        assertThat(service.get("name").asString()).isNotEqualTo("consul");
      }
    } finally {
      remote.whileDashboardIsStopped(() -> {});
    }
  }

  @Test
  void catalogMarksCoreServices() {
    String web = newServiceName("web");
    register(web, Map.of("host", "10.1.2.3", "port", 8080));

    String page = getPage("/").getBody();

    assertThat(rowOf(page, WORKSTATION, "consul")).contains("Core Service");
    assertThat(rowOf(page, RemoteWorkstation.NAME, "traefik")).contains("Core Service");
    assertThat(rowOf(page, WORKSTATION, web)).doesNotContain("Core Service");
  }

  @Test
  void servicePageMarksACoreService() {
    assertThat(getPage("/services/" + WORKSTATION + "/yard").getBody()).contains("Core Service");
    assertThat(getPage("/services/" + RemoteWorkstation.NAME + "/consul").getBody())
        .contains("Core Service");
  }

  @Test
  void removingACoreServicesStaticInstanceIsRefusedWithAReason() {
    String id = get(servicePath("consul")).getBody().get("instances").get(0).get("id").asString();

    ResponseEntity<JsonNode> response = delete(instancesPath("consul") + "/" + id);

    assertRefused(response, "Core Service");
    assertThat(get(servicePath("consul")).getBody().get("instances")).hasSize(1);
  }

  @Test
  void editingACoreServicesStaticInstanceIsRefusedWithAReason() {
    JsonNode before = get(servicePath("traefik")).getBody().get("instances").get(0);

    ResponseEntity<JsonNode> response =
        put(
            instancesPath("traefik") + "/" + before.get("id").asString(),
            Map.of("host", "127.0.0.1", "port", 9999));

    assertRefused(response, "Core Service");
    assertThat(get(servicePath("traefik")).getBody().get("instances").get(0).get("port").asInt())
        .isEqualTo(before.get("port").asInt());
  }

  @Test
  void unpublishingACoreServiceIsRefusedWithAReason() {
    ResponseEntity<JsonNode> response = delete(publicationPath("yard"));

    assertRefused(response, "Core Service");
    assertThat(get(servicePath("yard")).getBody().get("publication").get("scope").asString())
        .isEqualTo("local");
  }

  @Test
  void yardScopeForACoreServiceIsRefusedBecauseItsInstanceIsOnLoopback() {
    ResponseEntity<JsonNode> response = put(publicationPath("consul"), Map.of("scope", "yard"));

    assertRefused(response, "loopback");
    assertThat(response.getBody().get("detail").asString()).contains("other Workstations");
    assertThat(get(servicePath("consul")).getBody().get("publication").get("scope").asString())
        .isEqualTo("local");
  }

  @Test
  void publishingACoreServiceWithLocalScopeChangesNothing() {
    ResponseEntity<JsonNode> response = put(publicationPath("yard"), Map.of("scope", "local"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().get("publication").get("scope").asString()).isEqualTo("local");
  }

  @ParameterizedTest
  @ValueSource(strings = {"yard", "consul", "traefik"})
  void registeringAnInstanceUnderACoreServiceNameIsRefused(String name) {
    ResponseEntity<JsonNode> response = post(instancesPath(name), Map.of("port", 8080));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().get("detail").asString()).contains(name).contains("Core Service");
    assertThat(get(servicePath(name)).getBody().get("instances")).hasSize(1);
  }

  @ParameterizedTest
  @ValueSource(strings = {"yard", "consul", "traefik"})
  void bindingAContainerToACoreServiceIsRefused(String name) {
    ResponseEntity<JsonNode> response =
        post(containerBindingsPath(name), Map.of("container", "web", "port", 80));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().get("detail").asString()).contains("Core Service");
  }

  @Test
  void removingAContainerBindingOfACoreServiceIsRefusedWithAReason() {
    ResponseEntity<JsonNode> response = delete(containerBindingsPath("consul") + "/any");

    assertRefused(response, "Core Service");
  }

  @Test
  void aliasCannotTakeACoreServiceName() {
    ResponseEntity<JsonNode> response =
        put(
            "/api/workstation/aliases/traefik",
            Map.of("workstation", RemoteWorkstation.NAME, "service", "consul"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().get("detail").asString()).contains("Core Service");
  }

  @Test
  void coreServicePageHidesTheRemoveUnpublishAndScopeControls() {
    String page = getPage("/services/" + WORKSTATION + "/consul").getBody();

    String content = page.substring(page.indexOf("<main"));
    // Its one form is the Reachability test's, which only reads.
    assertThat(content).doesNotContain("method=\"post\"").doesNotContain("/edit\"");
    assertThat(content.split("<form", -1)).hasSize(2);
    assertThat(content)
        .doesNotContain("Unpublish")
        .doesNotContain("Share with the Yard")
        .doesNotContain(">Remove<");
  }

  @Test
  void coreServicePageFormsAreRefusedWithAReason() {
    String id = get(servicePath("yard")).getBody().get("instances").get(0).get("id").asString();
    String service = "/services/" + WORKSTATION + "/yard";

    assertThat(postForm(service + "/instances/" + id + "/remove", Map.of()).getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
    assertThat(postForm(service + "/unpublish", Map.of()).getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
    assertThat(postForm(service + "/publish", Map.of("scope", "yard")).getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
    assertThat(
            postForm(service + "/instances/" + id + "/edit", Map.of("port", "9999"))
                .getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
    assertThat(get(servicePath("yard")).getBody().get("instances").get(0).get("port").asInt())
        .isEqualTo(port);
  }

  /** The catalog's first row for {@code service} of {@code workstation}. */
  private static String rowOf(String page, String workstation, String service) {
    int link = page.indexOf("href=\"/services/" + workstation + "/" + service + "\"");
    assertThat(link).as("a row for %s", service).isNotNegative();
    return page.substring(page.lastIndexOf("<tr", link), page.indexOf("</tr>", link));
  }

  /** The ID of the remote Workstation's Instance of the Core Service {@code service}. */
  private String coreInstanceId(String service) {
    return remote.service(service).getBody().get("instances").get(0).get("id").asString();
  }

  /** {@code response} refuses a change to a Core Service, with a detail that has {@code reason}. */
  private static void assertRefused(ResponseEntity<JsonNode> response, String reason) {
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getHeaders().getContentType())
        .hasToString(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    assertThat(response.getBody().get("detail").asString()).contains(reason);
  }

  static void assertCheck(JsonNode instance, String path) {
    JsonNode check = instance.get("check");
    assertThat(check.get("type").asString()).isEqualTo("http");
    assertThat(check.get("path").asString()).isEqualTo(path);
    assertThat(check.get("interval").asString()).isEqualTo("10s");
    assertThat(check.get("timeout").asString()).isEqualTo("2s");
  }
}
