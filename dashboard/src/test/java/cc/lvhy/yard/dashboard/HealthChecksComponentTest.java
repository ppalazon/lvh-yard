package cc.lvhy.yard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/**
 * The health checks of Static Instances, chosen when they are registered, and each check's last
 * output.
 */
class HealthChecksComponentTest extends DashboardComponentTest {

  /** Long enough for a check with a 1 s interval to run a few times. */
  private static final Duration CHECK_TIMEOUT = Duration.ofSeconds(10);

  @Test
  void httpCheckAgainstAnInstanceAnsweringAnErrorIsCriticalWithThatAnswer() {
    String api = newServiceName("api");
    int port = newLanPort();
    answerHttpOnLanAddress(port, "503 Service Unavailable", "warming up");

    register(
        api,
        Map.of("port", port, "check", Map.of("type", "http", "path", "/health", "interval", "1s")));

    await()
        .atMost(CHECK_TIMEOUT)
        .untilAsserted(() -> {
          JsonNode instance = get(servicePath(api)).getBody().get("instances").get(0);
          assertThat(instance.get("health").asString()).isEqualTo("critical");
          assertThat(
                  instance.get("checks").valueStream().map(check -> check.get("output").asString()))
              .anySatisfy(output ->
                  assertThat(output).contains("/health").contains("503").contains("warming up"));
        });
  }

  @Test
  void checkIntervalAndTimeoutDefaultToTenAndTwoSeconds() {
    String api = newServiceName("api");
    String redis = newServiceName("redis");

    register(api, Map.of("port", 8080, "check", Map.of("path", "/health")));
    register(redis, Map.of("host", "10.1.2.3", "port", 6379));

    JsonNode http = get(servicePath(api)).getBody().get("instances").get(0).get("check");
    assertThat(http.get("type").asString()).isEqualTo("http");
    assertThat(http.get("path").asString()).isEqualTo("/health");
    assertThat(http.get("interval").asString()).isEqualTo("10s");
    assertThat(http.get("timeout").asString()).isEqualTo("2s");
    JsonNode tcp = get(servicePath(redis)).getBody().get("instances").get(0).get("check");
    assertThat(tcp.get("type").asString()).isEqualTo("tcp");
    assertThat(tcp.has("path")).isFalse();
    assertThat(tcp.get("interval").asString()).isEqualTo("10s");
    assertThat(tcp.get("timeout").asString()).isEqualTo("2s");
  }

  @Test
  void checkKeepsTheIntervalAndTimeoutItIsGiven() {
    String api = newServiceName("api");

    register(
        api,
        Map.of(
            "port", 8080, "check", Map.of("type", "tcp", "interval", "90s", "timeout", "1500ms")));

    JsonNode check = get(servicePath(api)).getBody().get("instances").get(0).get("check");
    assertThat(check.get("interval").asString()).isEqualTo("90s");
    assertThat(check.get("timeout").asString()).isEqualTo("1500ms");
  }

  @ParameterizedTest
  @ValueSource(strings = {"grpc"})
  void checkThatIsNotHttpOrTcpIsRefused(String type) {
    ResponseEntity<JsonNode> response =
        post(
            instancesPath(newServiceName("api")),
            Map.of("port", 8080, "check", Map.of("type", type)));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().get("detail").asString()).contains("use http or tcp");
  }

  @ParameterizedTest
  @ValueSource(strings = {"ten seconds"})
  void intervalThatIsNotADurationIsRefused(String interval) {
    ResponseEntity<JsonNode> response =
        post(
            instancesPath(newServiceName("api")),
            Map.of("port", 8080, "check", Map.of("interval", interval)));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().get("detail").asString()).contains("not a duration");
  }

  @ParameterizedTest
  @ValueSource(strings = {"health", "/my health", "/a#b"})
  void pathAnHttpCheckCannotGetIsRefused(String path) {
    ResponseEntity<JsonNode> response =
        post(
            instancesPath(newServiceName("api")),
            Map.of("port", 8080, "check", Map.of("type", "http", "path", path)));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().get("detail").asString()).contains("start it with /");
  }
}
