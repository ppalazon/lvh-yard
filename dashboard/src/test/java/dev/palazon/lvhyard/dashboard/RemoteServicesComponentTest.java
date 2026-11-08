package dev.palazon.lvhyard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/** The Services of the Yard's other Workstations: seen from here, never changed from here. */
class RemoteServicesComponentTest extends DashboardComponentTest {

  /** How long the dashboard takes to answer a GET of {@code path}. */
  private Duration answerTime(String path) {
    long start = System.nanoTime();
    get(path);
    return Duration.ofNanos(System.nanoTime() - start);
  }

  /** The {@link RemoteWorkstation} among the Yard's remotes. */
  private static JsonNode remoteOf(JsonNode yard) {
    return yard.get("remotes")
        .valueStream()
        .filter(workstation -> workstation.get("name").asString().equals(RemoteWorkstation.NAME))
        .findFirst()
        .orElseThrow();
  }

  private static List<String> serviceNames(JsonNode workstation) {
    return workstation
        .get("services")
        .valueStream()
        .map(service -> service.get("name").asString())
        .toList();
  }

  private static JsonNode service(JsonNode workstation, String name) {
    return workstation
        .get("services")
        .valueStream()
        .filter(service -> service.get("name").asString().equals(name))
        .findFirst()
        .orElseThrow();
  }

  @Test
  void workstationNotInTheYardIsNotFound() {
    ResponseEntity<JsonNode> services = get("/api/services?workstation=ws-typo");
    ResponseEntity<JsonNode> service = get("/api/services/ws-typo/" + newServiceName("api"));

    assertThat(services.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(services.getBody().get("detail").asString())
        .contains("The Yard has no Workstation ws-typo");
    assertThat(service.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(service.getBody().get("detail").asString())
        .contains("The Yard has no Workstation ws-typo");
    assertThat(getPage("/services/ws-typo/web").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }
}
