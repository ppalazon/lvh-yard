package cc.lvhy.yard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.JsonNode;

class ServiceStatesComponentTest extends DashboardComponentTest {

  private void addHttpInstance(String service, String status) {
    int port = newLanPort();
    answerHttpOnLanAddress(port, status, "health");
    register(service, Map.of("port", port, "check", Map.of("path", "/", "interval", "1s")));
  }

  @ParameterizedTest
  @CsvSource({"429 Too Many Requests,warning", "503 Service Unavailable,critical"})
  void aPassingAndANonPassingInstanceAreDegraded(String status, String health) {
    String service = newServiceName("mixed");
    addHttpInstance(service, "200 OK");
    addHttpInstance(service, status);
    publish(service);

    await()
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(() -> {
          JsonNode body = get(servicePath(service)).getBody();
          assertThat(
                  body.get("instances")
                      .valueStream()
                      .map(instance -> instance.get("health").asString()))
              .containsExactlyInAnyOrder("passing", health);
          assertThat(body.get("state").asString()).isEqualTo("degraded");
          assertThat(body.get("warnings")).isEmpty();
        });
    String page = getPage("/services/ws-test/" + service).getBody();
    assertThat(page).doesNotContain("role=\"alert\"");
    if (health.equals("warning")) {
      assertThat(page).contains(badge("risk", "warning"));
    }
    assertThat(getPage("/?state=degraded").getBody())
        .contains("href=\"/services/ws-test/" + service + "\"")
        .contains("<option value=\"degraded\" selected=\"selected\">Degraded</option>")
        .contains(badge("risk", "Degraded"));
    assertThat(getPage("/?state=healthy").getBody())
        .doesNotContain("href=\"/services/ws-test/" + service + "\"");
  }

  @ParameterizedTest
  @CsvSource({
    "200 OK,passing,healthy",
    "429 Too Many Requests,warning,unhealthy",
    "503 Service Unavailable,critical,unhealthy"
  })
  void everyInstanceMustPassForHealthy(String status, String health, String state) {
    String service = newServiceName("same-health");
    addHttpInstance(service, status);
    addHttpInstance(service, status);
    publish(service);

    await()
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(() -> {
          JsonNode body = get(servicePath(service)).getBody();
          assertThat(
                  body.get("instances")
                      .valueStream()
                      .map(instance -> instance.get("health").asString()))
              .containsExactly(health, health);
          assertThat(body.get("state").asString()).isEqualTo(state);
        });
  }

  @Test
  void aPassingStaticInstanceDoesNotHideAFailingBoundInstance() {
    String service = newServiceName("priority");
    addHttpInstance(service, "200 OK");
    String container = newContainerName();
    // Nothing listens on port 81 of the container, so its Bound Instance fails.
    startContainer(container, 80, false);
    bind(service, container, 81);
    publish(service);

    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(() -> {
          JsonNode body = get(servicePath(service)).getBody();
          assertThat(body.get("instances")).hasSize(2);
          assertThat(body.get("state").asString()).isEqualTo("degraded");
          assertThat(body.get("routing").get("kind").asString()).isEqualTo("static");
        });
  }

  @Test
  void outOfRangeDoesNotChangeTheStateOnAnotherWorkstation() {
    String service = newServiceName("range");
    String passing =
        remote.register(service, Map.of("host", "10.9.8.7", "port", 8080)).get("id").asString();
    remote.deregisterCheckInConsul(passing);
    remote.register(service, Map.of("host", "127.0.0.1", "port", 1));
    remote.publish(service, "yard");

    await()
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(() -> {
          JsonNode seen = get("/api/services/" + RemoteWorkstation.NAME + "/" + service).getBody();
          assertThat(
                  seen.get("instances")
                      .valueStream()
                      .filter(instance -> instance.get("host").asString().equals("127.0.0.1"))
                      .map(instance -> instance.get("outOfRange").asBoolean()))
              .containsExactly(true);
          assertThat(seen.get("state").asString()).isEqualTo("degraded");
          assertThat(remote.service(service).getBody().get("state").asString())
              .isEqualTo("degraded");
        });
  }
}
