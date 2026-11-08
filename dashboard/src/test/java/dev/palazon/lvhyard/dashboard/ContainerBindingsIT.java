package dev.palazon.lvhyard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/**
 * Container Bindings, which give a Service a Bound Instance for each running container they match.
 */
class ContainerBindingsIT extends DashboardIT {

  /**
   * Long enough for Docker's event to reach the dashboard, and for it to register or remove the
   * Instance.
   */
  private static final Duration FOLLOW_TIMEOUT = Duration.ofSeconds(15);

  /** Where each Instance of this Workstation's {@code service} is, as {@code host:port}. */
  private List<String> instanceAddresses(String service) {
    return addresses(get(servicePath(service)).getBody());
  }

  /**
   * Where each Instance of the remote Workstation's {@code service} is, as this Workstation reads
   * it.
   */
  private List<String> remoteInstanceAddresses(String service) {
    return addresses(get("/api/services/" + RemoteWorkstation.NAME + "/" + service).getBody());
  }

  private static List<String> addresses(JsonNode service) {
    return service
        .get("instances")
        .valueStream()
        .map(instance -> instance.get("host").asString() + ":" + instance.get("port").asInt())
        .toList();
  }

  @Test
  void remoteServicesBindingsAreShownAndCannotBeChangedHere() {
    String web = newServiceName("web");
    remote.bind(web, newContainerName(), 80);

    JsonNode service = get("/api/services/" + RemoteWorkstation.NAME + "/" + web).getBody();
    ResponseEntity<JsonNode> change =
        post(
            "/api/services/" + RemoteWorkstation.NAME + "/" + web + "/container-bindings",
            Map.of("container", "web", "port", 80));

    assertThat(service.get("containerBindings").get(0).get("port").asInt()).isEqualTo(80);
    assertThat(change.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
  }
}
