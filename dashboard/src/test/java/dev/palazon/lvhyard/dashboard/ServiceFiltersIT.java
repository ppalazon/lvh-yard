package dev.palazon.lvhyard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/** The REST API's list of the Yard's Services, filtered by Workstation and by state. */
class ServiceFiltersIT extends DashboardIT {

  private static List<String> names(ResponseEntity<JsonNode> services) {
    assertThat(services.getStatusCode()).isEqualTo(HttpStatus.OK);
    return services.getBody().valueStream().map(service -> service.get("name").asString()).toList();
  }

  @Test
  void filtersByWorkstationAndStateTogether() {
    String own = newServiceName("web");
    register(own, Map.of("host", "10.1.2.3", "port", 8080));
    String other = newServiceName("web");
    remote.register(other, Map.of("host", "10.9.8.7", "port", 8080));

    List<String> names =
        names(get("/api/services?workstation=" + RemoteWorkstation.NAME + "&state=unpublished"));

    assertThat(names).contains(other).doesNotContain(own);
  }
}
