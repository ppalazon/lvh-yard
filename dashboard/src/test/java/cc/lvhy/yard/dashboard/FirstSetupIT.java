package cc.lvhy.yard.dashboard;

import static cc.lvhy.yard.dashboard.TraefikConfigurationComponentTest.serverUrlsAt;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
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
}
