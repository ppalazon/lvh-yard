package dev.palazon.lvhyard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** A Service whose Instance Priority is off: its Static and Bound Instances share the Route. */
class InstancePriorityOffIT extends InstancePriorityTestSupportIT {

  @Test
  void passingStaticAndBoundInstancesShareTheRouteAndACriticalOneLeavesIt() {
    String web = newServiceName("web");
    String terminal = staticInstance(web);
    String bound = boundInstance(web);

    put(publicationPath(web), Map.of("priority", List.of()));

    awaitRoutedTo(web, terminal, bound);
    JsonNode routing = get(servicePath(web)).getBody().get("routing");
    assertThat(routing.has("kind")).isFalse();
    assertThat(routing.get("off").asBoolean()).isTrue();
    assertThat(summaryCard(getPage("/services/ws-test/" + web).getBody(), "routing"))
        .contains("Every passing Instance")
        .contains("Instance Priority off");

    stopListeningOnLanAddress(port(terminal));

    awaitRoutedTo(web, bound);
  }

  @Test
  void withNoPassingInstanceTheRouteUsesEveryInstance() {
    String web = newServiceName("web");
    int first = newLanPort();
    int second = newLanPort();
    // Nothing listens on these ports, so both checks stay critical.
    register(web, Map.of("port", first, "check", Map.of("interval", "1s")));
    register(web, Map.of("port", second, "check", Map.of("interval", "1s")));

    put(publicationPath(web), Map.of("priority", List.of()));

    awaitRoutedTo(web, lanAddress() + ":" + first, lanAddress() + ":" + second);
    assertThat(summaryCard(getPage("/services/ws-test/" + web).getBody(), "routing"))
        .contains("Every Instance, as none is passing")
        .contains("Instance Priority off");
  }
}
