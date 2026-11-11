package cc.lvhy.yard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/** The REST API's list of the Yard's Services, filtered by Workstation and by state. */
class ServiceFiltersComponentTest extends DashboardComponentTest {

  private static List<String> names(ResponseEntity<JsonNode> services) {
    assertThat(services.getStatusCode()).isEqualTo(HttpStatus.OK);
    return services.getBody().valueStream().map(service -> service.get("name").asString()).toList();
  }

  @Test
  void filtersByState() {
    String web = newServiceName("web");
    String api = newServiceName("api");
    register(web, Map.of("host", "10.1.2.3", "port", 8080));
    register(api, Map.of("host", "10.1.2.3", "port", 9090));
    publish(api);

    List<String> unhealthy = names(get("/api/services?state=unhealthy"));

    assertThat(unhealthy).contains(api).doesNotContain(web);
    assertThat(names(get("/api/services?state=unpublished"))).contains(web).doesNotContain(api);
  }

  @Test
  void refusesAnUnknownState() {
    ResponseEntity<JsonNode> response = get("/api/services?state=sleepy");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().get("detail").asString())
        .isEqualTo(
            "The state is unpublished, unavailable, unhealthy, degraded or healthy, not sleepy.");
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
