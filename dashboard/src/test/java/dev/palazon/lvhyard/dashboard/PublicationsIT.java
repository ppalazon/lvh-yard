package dev.palazon.lvhyard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Publications of this Workstation's Services, made and removed through the REST API. */
class PublicationsIT extends DashboardIT {

  /**
   * Long enough for a check's first run, which Consul starts at a random point within its 10 s
   * interval.
   */
  private static final Duration CHECK_TIMEOUT = Duration.ofSeconds(20);

  private static boolean accessLog(JsonNode service) {
    return service.get("publication").get("accessLog").asBoolean();
  }

  private static List<String> priority(JsonNode service) {
    return service.get("priority").valueStream().map(JsonNode::asString).toList();
  }

  @Test
  void publicationWrittenBeforeInstancePriorityUsesTheDefaultOrder() {
    String api = newServiceName("api");
    remote.register(api, Map.of("host", "10.9.8.7", "port", 9090));
    // A Publication as LVH Yard stored it before Instance Priority: its Scope alone.
    remote.putConsulValue("lvh-yard/publications/" + api, "yard");

    assertThat(priority(remote.service(api).getBody())).containsExactly("static", "bound", "alias");
    assertThat(priority(get("/api/services/" + RemoteWorkstation.NAME + "/" + api).getBody()))
        .containsExactly("static", "bound", "alias");
  }
}
