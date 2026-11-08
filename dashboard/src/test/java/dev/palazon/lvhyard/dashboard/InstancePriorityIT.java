package dev.palazon.lvhyard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import tools.jackson.databind.JsonNode;

/**
 * The Instance Priority: a Route uses only the first Instance kind with a passing Instance, by
 * default Static, then Bound, and only its passing Instances.
 */
class InstancePriorityIT extends InstancePriorityTestSupportIT {

  @Test
  void routeMovesToTheBoundInstanceWhenTheStaticInstanceStopsAndBackWhenItStartsAgain() {
    String web = newServiceName("web");
    int port = newLanPort();
    listenOnLanAddress(port);
    register(web, Map.of("port", port, "check", Map.of("interval", "1s")));
    String bound = boundInstance(web);
    publish(web);
    String terminal = lanAddress() + ":" + port;
    awaitRoutedTo(web, terminal);

    stopListeningOnLanAddress(port);

    awaitRoutedTo(web, bound);

    listenOnLanAddress(port);

    awaitRoutedTo(web, terminal);
  }

  @Test
  void everyPassingInstanceOfTheChosenKindSharesTheRoute() {
    String web = newServiceName("web");
    String first = staticInstance(web);
    String second = staticInstance(web);
    register(web, Map.of("host", "10.1.2.3", "port", 8080, "check", Map.of("interval", "1s")));
    boundInstance(web);

    publish(web);

    awaitRoutedTo(web, first, second);
  }

  @Test
  void serviceWithNoPassingInstanceIsRoutedToEveryInstance() {
    String web = newServiceName("web");
    register(web, Map.of("host", "10.1.2.3", "port", 8080, "check", Map.of("interval", "1s")));
    String name = newContainerName();
    GenericContainer<?> container = startYardNetworkContainer(name, 80);
    // Nothing listens on this port, so the Bound Instance's check stays critical.
    bind(web, name, 81);

    publish(web);

    awaitRoutedTo(web, "10.1.2.3:8080", containerAddress(container) + ":81");
    JsonNode routing = get(servicePath(web)).getBody().get("routing");
    assertThat(routing.has("kind")).isFalse();
    assertThat(routing.has("off")).isFalse();
    assertThat(routing.get("instances")).hasSize(2);
    assertThat(summaryCard(getPage("/services/ws-test/" + web).getBody(), "routing"))
        .contains("Every Instance, as none is passing");
  }

  @Test
  void apiAndServicePageShowTheKindInUseAndTheKindsWaiting() {
    String web = newServiceName("web");
    staticInstance(web);
    boundInstance(web);

    publish(web);

    await()
        .atMost(SWITCH_TIMEOUT)
        .untilAsserted(() -> {
          JsonNode service = get(servicePath(web)).getBody();
          String terminal =
              service
                  .get("instances")
                  .valueStream()
                  .filter(instance -> !instance.has("containerBinding"))
                  .findFirst()
                  .orElseThrow()
                  .get("id")
                  .asString();
          assertThat(texts(service.get("priority"))).containsExactly("static", "bound", "alias");
          assertThat(service.get("routing").path("kind").asString()).isEqualTo("static");
          assertThat(texts(service.get("routing").get("instances"))).containsExactly(terminal);
          assertThat(summaryCard(getPage("/services/ws-test/" + web).getBody(), "routing"))
              .contains("Static")
              .contains("Bound waiting");
        });
  }

  @Test
  void unpublishedServiceHasNoInstancePriorityOrRouting() {
    String web = newServiceName("web");
    staticInstance(web);

    JsonNode service = get(servicePath(web)).getBody();

    assertThat(service.has("priority")).isFalse();
    assertThat(service.has("routing")).isFalse();
  }
}
