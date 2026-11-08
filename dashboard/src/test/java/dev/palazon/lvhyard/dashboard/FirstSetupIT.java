package dev.palazon.lvhyard.dashboard;

import static dev.palazon.lvhyard.dashboard.TraefikConfigurationIT.routedHostnames;
import static dev.palazon.lvhyard.dashboard.TraefikConfigurationIT.serverUrlsAt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/**
 * The first start of a dashboard sets up yard, consul-ui and traefik-admin, and then they are
 * ordinary Services. The tests that change them do it on the remote Workstation, so the other tests
 * keep this Workstation's.
 */
class FirstSetupIT extends DashboardIT {

  private static final List<String> OWN_SERVICES = List.of("yard", "consul-ui", "traefik-admin");

  /**
   * Removes the remote Workstation's three Services and the marker, and starts its dashboard again
   * with {@code settings}, so the setup writes them again.
   */
  private void setUpAgain(String... settings) {
    List<String> ids = new ArrayList<>();
    OWN_SERVICES.forEach(name -> ids.addAll(instanceIds(name)));
    remote.whileDashboardIsStopped(
        () -> {
          ids.forEach(remote::deregisterInConsul);
          OWN_SERVICES.forEach(name -> remote.deleteConsulValue("lvh-yard/publications/" + name));
          remote.deleteConsulValue(RemoteWorkstation.FIRST_SETUP);
        },
        settings);
  }

  /** The IDs of the Instances of the remote Workstation's {@code service}, or none. */
  private List<String> instanceIds(String service) {
    ResponseEntity<JsonNode> response = remote.get(servicePath(service));
    List<String> ids = new ArrayList<>();
    if (response.getStatusCode() == HttpStatus.OK) {
      response
          .getBody()
          .get("instances")
          .forEach(instance -> ids.add(instance.get("id").asString()));
    }
    return ids;
  }

  static void assertCheck(JsonNode instance, String path) {
    JsonNode check = instance.get("check");
    assertThat(check.get("type").asString()).isEqualTo("http");
    assertThat(check.get("path").asString()).isEqualTo(path);
    assertThat(check.get("interval").asString()).isEqualTo("10s");
    assertThat(check.get("timeout").asString()).isEqualTo("2s");
  }

  @Test
  void consulUiAndTraefikAdminAreOnLoopbackWithHealthChecksAndLocalScope() {
    JsonNode consul = get(servicePath("consul-ui")).getBody();
    JsonNode traefik = get(servicePath("traefik-admin")).getBody();

    assertThat(consul.get("hostname").asString()).isEqualTo("consul-ui.lvh.test");
    assertThat(consul.get("publication").get("scope").asString()).isEqualTo("local");
    assertThat(consul.get("instances")).hasSize(1);
    JsonNode consulInstance = consul.get("instances").get(0);
    assertThat(consulInstance.get("host").asString()).isEqualTo("127.0.0.1");
    assertThat(consulInstance.get("port").asInt())
        .isEqualTo(consulContainer.getMappedPort(TestcontainersConfiguration.CONSUL_HTTP_PORT));
    assertCheck(consulInstance, "/v1/status/leader");

    assertThat(traefik.get("hostname").asString()).isEqualTo("traefik-admin.lvh.test");
    assertThat(traefik.get("publication").get("scope").asString()).isEqualTo("local");
    JsonNode traefikInstance = traefik.get("instances").get(0);
    assertThat(traefikInstance.get("host").asString()).isEqualTo("127.0.0.1");
    assertThat(traefikInstance.get("port").asInt())
        .as("YARD_TRAEFIK_ADMIN_PORT")
        .isEqualTo(TestcontainersConfiguration.TRAEFIK_ADMIN_PORT);
    assertCheck(traefikInstance, "/ping");
  }

  @Test
  void theThreeServicesAreRoutedAtTheirHostnames() {
    JsonNode http = get("/traefik/configuration").getBody().get("http");

    assertThat(serverUrlsAt(http, "yard.lvh.test")).containsExactly("http://127.0.0.1:" + port);
    assertThat(serverUrlsAt(http, "consul-ui.lvh.test"))
        .containsExactly(
            "http://127.0.0.1:"
                + consulContainer.getMappedPort(TestcontainersConfiguration.CONSUL_HTTP_PORT));
    assertThat(serverUrlsAt(http, "traefik-admin.lvh.test"))
        .containsExactly("http://127.0.0.1:" + TestcontainersConfiguration.TRAEFIK_ADMIN_PORT);
  }

  @Test
  void theSetupWritesTheTimeAtTheMarker() {
    String marker = remote.consulValue(RemoteWorkstation.FIRST_SETUP);

    assertThat(marker).isNotNull();
    assertThat(Instant.parse(marker)).isBefore(Instant.now());
  }

  @Test
  void unpublishedYardHasNoRoute() {
    try {
      remote.unpublish("yard");

      assertThat(routedHostnames(remote.traefikConfiguration().get("http")))
          .doesNotContain("yard.lvh.test");
      assertThat(remote.get("/actuator/health").getStatusCode()).isEqualTo(HttpStatus.OK);
    } finally {
      remote.publish("yard", "local");
    }
  }

  @Test
  void theThreeServicesTakeYardScopeAndAnInstancePriority() {
    try {
      remote.publish("traefik-admin", Map.of("scope", "yard", "priority", List.of("static")));

      JsonNode service = remote.service("traefik-admin").getBody();
      assertThat(service.get("publication").get("scope").asString()).isEqualTo("yard");
      assertThat(service.get("priority").get(0).asString()).isEqualTo("static");
    } finally {
      remote.publish(
          "traefik-admin",
          Map.of("scope", "local", "priority", List.of("static", "bound", "alias")));
    }
  }

  @Test
  void removedServiceStaysRemovedAfterARestart() {
    try {
      List<String> ids = instanceIds("consul-ui");
      ids.forEach(id -> remote.remove("consul-ui", id));
      remote.unpublish("consul-ui");

      remote.whileDashboardIsStopped(() -> {});

      // The setup runs in the background, so a wrong one would put it back a little later.
      await()
          .during(Duration.ofSeconds(2))
          .atMost(Duration.ofSeconds(5))
          .until(
              () -> remote.get(servicePath("consul-ui")).getStatusCode() == HttpStatus.NOT_FOUND);
    } finally {
      setUpAgain();
    }
  }

  @Test
  void theSetupLeavesAServiceWithOneOfTheNamesAlone() {
    List<String> ids = new ArrayList<>();
    OWN_SERVICES.forEach(name -> ids.addAll(instanceIds(name)));
    try {
      remote.whileDashboardIsStopped(() -> {
        ids.forEach(remote::deregisterInConsul);
        OWN_SERVICES.forEach(name -> remote.deleteConsulValue("lvh-yard/publications/" + name));
        remote.deleteConsulValue(RemoteWorkstation.FIRST_SETUP);
        remote.registerInConsul("my-yard", "yard", "10.9.8.7", 9000);
      });

      JsonNode yard = remote.service("yard").getBody();
      assertThat(yard.has("publication")).as("yard is not published").isFalse();
      assertThat(yard.get("instances")).hasSize(1);
      assertThat(yard.get("instances").get(0).get("id").asString()).isEqualTo("my-yard");
      for (String name : List.of("consul-ui", "traefik-admin")) {
        JsonNode service = remote.service(name).getBody();
        assertThat(service.get("instances")).as(name).hasSize(1);
        assertThat(service.get("publication").get("scope").asString()).isEqualTo("local");
      }
    } finally {
      setUpAgain();
    }
  }

  @Test
  void theSetupLeavesAYardWithOnlyAPublicationAlone() {
    try {
      instanceIds("yard").forEach(id -> remote.remove("yard", id));
      remote.whileDashboardIsStopped(() -> remote.deleteConsulValue(RemoteWorkstation.FIRST_SETUP));

      JsonNode yard = remote.service("yard").getBody();
      assertThat(yard.get("instances")).isEmpty();
      assertThat(yard.get("publication").get("scope").asString()).isEqualTo("local");
    } finally {
      setUpAgain();
    }
  }

  @Test
  void theSetupLeavesAYardWithOnlyAContainerBindingAlone() {
    String container = "no-such-container";
    try {
      instanceIds("yard").forEach(id -> remote.remove("yard", id));
      remote.unpublish("yard");
      remote.bind("yard", container, 80);
      remote.whileDashboardIsStopped(() -> remote.deleteConsulValue(RemoteWorkstation.FIRST_SETUP));

      JsonNode yard = remote.service("yard").getBody();
      assertThat(yard.get("instances")).isEmpty();
      assertThat(yard.has("publication")).as("yard is not published").isFalse();
      assertThat(yard.get("containerBindings")).hasSize(1);
    } finally {
      remote.unbind("yard", container + ":80");
      setUpAgain();
    }
  }

  @Test
  void theSetupFinishesAServiceThatItBeganOnAnEarlierStart() {
    List<String> ids = new ArrayList<>();
    List.of("yard", "consul-ui").forEach(name -> ids.addAll(instanceIds(name)));
    try {
      // As if the dashboard stopped after it registered traefik-admin and before it published it.
      // The test dashboard starts on another port, so yard cannot be the one.
      remote.whileDashboardIsStopped(() -> {
        ids.forEach(remote::deregisterInConsul);
        OWN_SERVICES.forEach(name -> remote.deleteConsulValue("lvh-yard/publications/" + name));
        remote.deleteConsulValue(RemoteWorkstation.FIRST_SETUP);
        remote.putConsulValue(RemoteWorkstation.FIRST_SETUP_NAMES, String.join(",", OWN_SERVICES));
      });

      for (String name : OWN_SERVICES) {
        JsonNode service = remote.service(name).getBody();
        assertThat(service.get("instances")).as(name).hasSize(1);
        assertThat(service.get("publication").get("scope").asString()).as(name).isEqualTo("local");
      }
      assertThat(remote.consulValue(RemoteWorkstation.FIRST_SETUP_NAMES)).isNull();
    } finally {
      setUpAgain();
    }
  }

  @Test
  void yardFollowsTheBindAddressAndTraefikAdminTheAdminPort() throws IOException {
    int adminPort;
    try (ServerSocket free = new ServerSocket(0)) {
      adminPort = free.getLocalPort();
    }
    try {
      setUpAgain("--server.address=0.0.0.0", "--lvh-yard.traefik.admin-port=" + adminPort);

      JsonNode yard = remote.service("yard").getBody().get("instances");
      assertThat(yard).hasSize(1);
      assertThat(yard.get(0).get("host").asString()).isEqualTo("127.0.0.1");
      assertThat(yard.get(0).get("port").asInt()).isEqualTo(remote.dashboardPort());
      JsonNode traefik = remote.service("traefik-admin").getBody().get("instances");
      assertThat(traefik).hasSize(1);
      assertThat(traefik.get(0).get("port").asInt()).isEqualTo(adminPort);
    } finally {
      setUpAgain();
    }
  }

  @Test
  void consulThatDoesNotAnswerAtStartIsTriedAgainWithoutHoldingUpTheStart() {
    Instant[] starting = new Instant[1];

    Duration start;
    try {
      remote.whileDashboardIsStopped(() -> {
        remote.deleteConsulValue(RemoteWorkstation.FIRST_SETUP);
        remote.stopAnswering();
        starting[0] = Instant.now();
      });
      start = Duration.between(starting[0], Instant.now());
    } finally {
      remote.answerAgain();
    }

    // A Consul call that gets no answer waits 3 s; the start must not wait for one.
    assertThat(start).isLessThan(Duration.ofSeconds(3));
    await()
        .atMost(Duration.ofSeconds(30))
        .ignoreExceptions()
        .until(() -> remote.consulValue(RemoteWorkstation.FIRST_SETUP) != null);
  }

  @Test
  void teammatesThreeServicesAreOrdinaryRemoteServices() {
    List<String> routed = routedHostnames(get("/traefik/configuration").getBody().get("http"));
    for (String name : OWN_SERVICES) {
      JsonNode service = get("/api/services/" + RemoteWorkstation.NAME + "/" + name).getBody();

      assertThat(service.has("core")).as(name).isFalse();
      assertThat(service.get("instances")).as(name).hasSize(1);
      assertThat(service.get("instances").get(0).has("core")).as(name).isFalse();
      assertThat(service.has("hostname")).as(name + " has Local Scope").isFalse();
      assertThat(routed).doesNotContain(name + "." + RemoteWorkstation.NAME + ".lvh.test");
    }
  }
}
