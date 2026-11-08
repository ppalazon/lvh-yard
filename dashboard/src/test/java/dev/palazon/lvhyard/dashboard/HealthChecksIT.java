package dev.palazon.lvhyard.dashboard;

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
class HealthChecksIT extends DashboardIT {

  /** Long enough for a check with a 1 s interval to run a few times. */
  private static final Duration CHECK_TIMEOUT = Duration.ofSeconds(10);

  @Test
  void httpCheckAgainstAnInstanceAnsweringAnErrorIsCriticalWithThatAnswer() {
    String api = newServiceName("api");
    answerHttpOnLanAddress(18081, "503 Service Unavailable", "warming up");

    register(
        api,
        Map.of(
            "port", 18081, "check", Map.of("type", "http", "path", "/health", "interval", "1s")));

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

    register(api, Map.of("port", 8080, "check", Map.of("path", "/health")));

    JsonNode check = get(servicePath(api)).getBody().get("instances").get(0).get("check");
    assertThat(check.get("type").asString()).isEqualTo("http");
    assertThat(check.get("path").asString()).isEqualTo("/health");
    assertThat(check.get("interval").asString()).isEqualTo("10s");
    assertThat(check.get("timeout").asString()).isEqualTo("2s");
  }

  @Test
  void instanceWithoutCheckHasTcpCheckWithTheDefaults() {
    String redis = newServiceName("redis");

    register(redis, Map.of("host", "10.1.2.3", "port", 6379));

    JsonNode check = get(servicePath(redis)).getBody().get("instances").get(0).get("check");
    assertThat(check.get("type").asString()).isEqualTo("tcp");
    assertThat(check.has("path")).isFalse();
    assertThat(check.get("interval").asString()).isEqualTo("10s");
    assertThat(check.get("timeout").asString()).isEqualTo("2s");
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

  @Test
  void httpCheckAgainstAnInstanceAnsweringOkIsPassing() {
    String api = newServiceName("api");
    answerHttpOnLanAddress(18082, "200 OK", "fine");

    register(api, Map.of("port", 18082, "check", Map.of("path", "/", "interval", "1s")));

    await()
        .atMost(CHECK_TIMEOUT)
        .untilAsserted(() ->
            assertThat(
                    get(servicePath(api))
                        .getBody()
                        .get("instances")
                        .get(0)
                        .get("health")
                        .asString())
                .isEqualTo("passing"));
  }

  @Test
  void scriptCheckIsRefused() {
    String api = newServiceName("api");

    ResponseEntity<JsonNode> response =
        post(instancesPath(api), Map.of("port", 8080, "check", Map.of("type", "script")));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().get("detail").asString())
        .contains("Script checks are not offered");
    assertThat(get(servicePath(api)).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @ParameterizedTest
  @ValueSource(strings = {"grpc", "HTTP"})
  void checkThatIsNotHttpOrTcpIsRefused(String type) {
    ResponseEntity<JsonNode> response =
        post(
            instancesPath(newServiceName("api")),
            Map.of("port", 8080, "check", Map.of("type", type)));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().get("detail").asString()).contains("use http or tcp");
  }

  @ParameterizedTest
  @ValueSource(strings = {"10", "ten seconds", "0s", "1.5s", "-1s"})
  void intervalThatIsNotADurationIsRefused(String interval) {
    ResponseEntity<JsonNode> response =
        post(
            instancesPath(newServiceName("api")),
            Map.of("port", 8080, "check", Map.of("interval", interval)));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().get("detail").asString()).contains("not a duration");
  }

  @Test
  void intervalUnderOneSecondIsRefused() {
    ResponseEntity<JsonNode> response =
        post(
            instancesPath(newServiceName("api")),
            Map.of("port", 8080, "check", Map.of("interval", "500ms")));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().get("detail").asString()).contains("once a second");
  }

  @Test
  void timeoutThatIsNotADurationIsRefused() {
    ResponseEntity<JsonNode> response =
        post(
            instancesPath(newServiceName("api")),
            Map.of("port", 8080, "check", Map.of("timeout", "2")));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().get("detail").asString())
        .contains("timeout")
        .contains("not a duration");
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

  @Test
  void tcpCheckWithAPathIsRefused() {
    ResponseEntity<JsonNode> response =
        post(
            instancesPath(newServiceName("api")),
            Map.of("port", 8080, "check", Map.of("type", "tcp", "path", "/health")));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().get("detail").asString()).contains("A TCP check has no path");
  }
}
