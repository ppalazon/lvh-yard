package cc.lvhy.yard.dashboard;

import static cc.lvhy.yard.dashboard.RemoteServicesComponentTest.remoteOf;
import static cc.lvhy.yard.dashboard.RemoteServicesComponentTest.serviceNames;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/** What the Yard shows and answers while a Workstation of the Yard does not answer. */
class RemoteWorkstationUnreachableComponentTest extends DashboardComponentTest {

  /** How long the dashboard takes to answer a GET of {@code path}. */
  private Duration answerTime(String path) {
    long start = System.nanoTime();
    get(path);
    return Duration.ofNanos(System.nanoTime() - start);
  }

  @Test
  void workstationThatDoesNotAnswerIsUnreachableAndComesBackByItself() {
    String web = newServiceName("web");
    register(web, Map.of("host", "10.1.2.3", "port", 8080));
    String api = newServiceName("api");
    remote.register(api, Map.of("host", "10.9.8.7", "port", 9090));
    remote.publish(api, "yard");
    publish(web);
    // The Alias makes this Workstation's own Service read the Remote Service as its fallback.
    post(
        AliasesComponentTest.aliasesPath(web),
        Map.of("workstation", RemoteWorkstation.NAME, "service", api));

    remote.stopAnswering();
    try {
      JsonNode yard =
          await()
              .atMost(Duration.ofSeconds(60))
              .until(
                  () -> get("/api/yard").getBody(),
                  body -> remoteOf(body).get("unreachable").asBoolean());

      assertThat(remoteOf(yard).get("services")).isEmpty();
      // A Workstation that keeps failing is not asked again for a while, so the Yard answers
      // without waiting.
      await()
          .atMost(Duration.ofSeconds(30))
          .until(() -> answerTime("/api/yard"), time -> time.compareTo(Duration.ofSeconds(1)) < 0);
      for (String own : List.of("/api/workstation/services", servicePath(web))) {
        await()
            .atMost(Duration.ofSeconds(30))
            .until(() -> answerTime(own), time -> time.compareTo(Duration.ofSeconds(1)) < 0);
      }
      assertThat(serviceNames(yard.get("home"))).contains(web);
      ResponseEntity<JsonNode> one = get("/api/services/" + RemoteWorkstation.NAME + "/" + api);
      assertThat(one.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
      assertThat(one.getBody().get("detail").asString())
          .contains(RemoteWorkstation.NAME + " is unreachable");
      assertThat(get("/api/services?workstation=" + RemoteWorkstation.NAME).getStatusCode())
          .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
      assertThat(getPage("/services/" + RemoteWorkstation.NAME + "/" + api).getStatusCode())
          .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
      String catalog = getPage("/").getBody();
      int heading = catalog.indexOf("<h2>" + RemoteWorkstation.NAME + "</h2>");
      assertThat(heading).as("a section for the unreachable Workstation").isNotNegative();
      assertThat(catalog.substring(heading))
          .contains("Workstation unreachable")
          .contains(
              "<code class=\"rounded bg-muted px-2 py-1 font-mono\">lvh-yard ws compose logs"
                  + " consul</code>")
          .doesNotContain(api);
      assertThat(catalog).contains(web);
      assertThat(
              get("/api/services")
                  .getBody()
                  .valueStream()
                  .map(service -> service.get("name").asString()))
          .contains(web)
          .doesNotContain(api);
    } finally {
      remote.answerAgain();
    }

    JsonNode yard =
        await()
            .atMost(Duration.ofSeconds(60))
            .until(
                () -> get("/api/yard").getBody(),
                body -> !remoteOf(body).get("unreachable").asBoolean());
    assertThat(serviceNames(remoteOf(yard))).contains(api);
  }
}
