package dev.palazon.lvhyard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class ServiceStatesIT extends DashboardIT {

  private void addHttpInstance(String service, String status) {
    int port = newLanPort();
    answerHttpOnLanAddress(port, status, "health");
    register(service, Map.of("port", port, "check", Map.of("path", "/", "interval", "1s")));
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
