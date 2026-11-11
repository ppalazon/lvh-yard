package cc.lvhy.yard.dashboard;

import static cc.lvhy.yard.dashboard.TraefikConfigurationComponentTest.routedHostnames;
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
class FirstSetupComponentTest extends DashboardComponentTest {

  private static final List<String> OWN_SERVICES = List.of("yard", "consul-ui", "traefik-admin");

  static void assertCheck(JsonNode instance, String path) {
    JsonNode check = instance.get("check");
    assertThat(check.get("type").asString()).isEqualTo("http");
    assertThat(check.get("path").asString()).isEqualTo(path);
    assertThat(check.get("interval").asString()).isEqualTo("10s");
    assertThat(check.get("timeout").asString()).isEqualTo("2s");
  }

  @Test
  void yardIsTheDashboardWithAHealthCheckAndLocalScope() {
    ResponseEntity<JsonNode> response = get(servicePath("yard"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode yard = response.getBody();
    assertThat(yard.get("publication").get("scope").asString()).isEqualTo("local");
    assertThat(yard.get("hostname").asString()).isEqualTo("yard.lvh.test");
    assertThat(yard.get("instances")).hasSize(1);
    JsonNode instance = yard.get("instances").get(0);
    assertThat(instance.get("host").asString()).isEqualTo("127.0.0.1");
    assertThat(instance.get("port").asInt()).isEqualTo(port);
    assertCheck(instance, "/actuator/health");
  }

  @Test
  void theThreeServicesHaveNoAccessLog() {
    for (String service : List.of("yard", "consul-ui", "traefik-admin")) {
      assertThat(
              get(servicePath(service)).getBody().get("publication").get("accessLog").asBoolean())
          .as(service)
          .isFalse();
    }
  }

  @Test
  void servicesHaveNoCoreField() {
    JsonNode yard = get(servicePath("yard")).getBody();

    assertThat(yard.has("core")).isFalse();
    assertThat(yard.get("instances").get(0).has("core")).isFalse();
  }

  @Test
  void consulsOwnEntryIsNotInTheCatalog() {
    assertThat(get(servicePath("consul")).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    for (JsonNode service : get("/api/services").getBody()) {
      assertThat(service.get("name").asString()).isNotEqualTo("consul");
    }
  }

  @Test
  void servicePageOfYardShowsTheSameControlsAsAnyService() {
    String page = getPage("/services/" + WORKSTATION + "/yard").getBody();

    assertThat(page.substring(page.indexOf("<main")))
        .doesNotContain("Core Service")
        .contains("Published")
        .contains("Share with the Yard")
        .contains("Instance Priority")
        .contains("aria-label=\"Remove Static Instance");
  }

  @Test
  void yardAndTraefikAreFreeNames() {
    create("traefik");
    ResponseEntity<JsonNode> added =
        post(instancesPath("traefik"), Map.of("host", "10.1.2.3", "port", 8080));
    try {
      assertThat(added.getStatusCode().is2xxSuccessful()).isTrue();
    } finally {
      delete(instancesPath("traefik") + "/" + added.getBody().get("id").asString());
    }
  }

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
          OWN_SERVICES.forEach(name -> {
            remote.deleteConsulValue("lvh-yard/publications/" + name);
            remote.deleteConsulValue("lvh-yard/services/" + name);
          });
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

  @Test
  void theSetupWritesTheTimeAtTheMarker() {
    String marker = remote.consulValue(RemoteWorkstation.FIRST_SETUP);

    assertThat(marker).isNotNull();
    assertThat(Instant.parse(marker)).isBefore(Instant.now());
  }

  @Test
  void turningTraefikOffLeavesAnExistingTraefikAdminServiceAlone() {
    String marker = remote.consulValue(RemoteWorkstation.FIRST_SETUP);
    remote.whileDashboardRunsWith(
        () -> {
          JsonNode service = remote.service("traefik-admin").getBody();
          assertThat(service.get("instances")).hasSize(1);
          assertThat(service.get("publication").get("scope").asString()).isEqualTo("local");
          assertThat(remote.consulValue(RemoteWorkstation.FIRST_SETUP)).isEqualTo(marker);
        },
        "--YARD_TRAEFIK_ENABLED=false");
  }

  @Test
  void firstSetupWithoutTraefikWritesOnlyYardAndConsulAndDoesNotRunAgainWhenEnabled() {
    try {
      setUpAgain("--YARD_TRAEFIK_ENABLED=false");

      for (String name : List.of("yard", "consul-ui")) {
        JsonNode service = remote.service(name).getBody();
        assertThat(service.get("instances")).as(name).hasSize(1);
        assertThat(service.get("publication").get("scope").asString()).isEqualTo("local");
      }
      assertThat(remote.get(servicePath("traefik-admin")).getStatusCode())
          .isEqualTo(HttpStatus.NOT_FOUND);
      String marker = remote.consulValue(RemoteWorkstation.FIRST_SETUP);
      assertThat(Instant.parse(marker)).isBefore(Instant.now());
      assertThat(remote.consulValue(RemoteWorkstation.FIRST_SETUP_NAMES)).isNull();

      remote.whileDashboardIsStopped(() -> {}, "--YARD_TRAEFIK_ENABLED=true");

      assertThat(remote.consulValue(RemoteWorkstation.FIRST_SETUP)).isEqualTo(marker);
      assertThat(remote.get(servicePath("traefik-admin")).getStatusCode())
          .isEqualTo(HttpStatus.NOT_FOUND);
    } finally {
      setUpAgain();
    }
  }

  @Test
  void resumedFirstSetupWithoutTraefikDoesNotRegisterTraefikAdmin() {
    List<String> ids = new ArrayList<>();
    OWN_SERVICES.forEach(name -> ids.addAll(instanceIds(name)));
    try {
      remote.whileDashboardIsStopped(
          () -> {
            ids.forEach(remote::deregisterInConsul);
            OWN_SERVICES.forEach(name -> {
              remote.deleteConsulValue("lvh-yard/publications/" + name);
              remote.deleteConsulValue("lvh-yard/services/" + name);
            });
            remote.deleteConsulValue(RemoteWorkstation.FIRST_SETUP);
            remote.putConsulValue(RemoteWorkstation.FIRST_SETUP_NAMES, String.join(",", OWN_SERVICES));
          },
          "--YARD_TRAEFIK_ENABLED=false");

      assertThat(remote.service("yard").getBody().get("instances")).hasSize(1);
      assertThat(remote.service("consul-ui").getBody().get("instances")).hasSize(1);
      assertThat(remote.get(servicePath("traefik-admin")).getStatusCode())
          .isEqualTo(HttpStatus.NOT_FOUND);
      assertThat(remote.consulValue(RemoteWorkstation.FIRST_SETUP)).isNotNull();
      assertThat(remote.consulValue(RemoteWorkstation.FIRST_SETUP_NAMES)).isNull();
    } finally {
      setUpAgain();
    }
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
      remote.publish("traefik-admin", Map.of("scope", "yard"));
      remote.setInstancePriority("traefik-admin", Map.of("on", false));

      JsonNode service = remote.service("traefik-admin").getBody();
      assertThat(service.get("publication").get("scope").asString()).isEqualTo("yard");
      assertThat(service.get("instancePriority").asBoolean()).isFalse();
    } finally {
      remote.publish("traefik-admin", Map.of("scope", "local"));
      remote.setInstancePriority("traefik-admin", Map.of("on", true));
    }
  }

  @Test
  void removedServiceStaysRemovedAfterARestart() {
    try {
      remote.delete("consul-ui");

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
        OWN_SERVICES.forEach(name -> {
          remote.deleteConsulValue("lvh-yard/publications/" + name);
          remote.deleteConsulValue("lvh-yard/services/" + name);
        });
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
        OWN_SERVICES.forEach(name -> {
          remote.deleteConsulValue("lvh-yard/publications/" + name);
          remote.deleteConsulValue("lvh-yard/services/" + name);
        });
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
