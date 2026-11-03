package dev.palazon.lvhyard.dashboard;

import static dev.palazon.lvhyard.dashboard.TraefikConfigurationTest.routedHostnames;
import static dev.palazon.lvhyard.dashboard.TraefikConfigurationTest.serverUrlsAt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
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
        .as("YARD_TRAEFIK_ADMIN_PORT's default")
        .isEqualTo(8080);
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
  void consulThatDoesNotAnswerAtStartIsTriedAgain() {
    String consulId = coreInstanceId("consul");

    try {
      remote.whileDashboardIsStopped(() -> {
        remote.deregisterInConsul(consulId);
        remote.stopAnswering();
      });
    } finally {
      remote.answerAgain();
    }

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
    assertThat(serverUrlsAt(http, "traefik.lvh.test")).containsExactly("http://127.0.0.1:8080");
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

  static void assertCheck(JsonNode instance, String path) {
    JsonNode check = instance.get("check");
    assertThat(check.get("type").asString()).isEqualTo("http");
    assertThat(check.get("path").asString()).isEqualTo(path);
    assertThat(check.get("interval").asString()).isEqualTo("10s");
    assertThat(check.get("timeout").asString()).isEqualTo("2s");
  }
}
