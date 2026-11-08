package dev.palazon.lvhyard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import tools.jackson.databind.JsonNode;

/**
 * Bound Instances that need real containers: those of the other Workstation, whose own dashboard
 * follows the real Docker, and those whose checks Consul runs.
 */
class BoundInstancesIT extends DashboardIT {

  private static final Duration FOLLOW_TIMEOUT = Duration.ofSeconds(15);

  private GenericContainer<?> startComposeOnYard(String name, String project, int port) {
    return start(
        TestcontainersConfiguration.listener(name, port)
            .withNetwork(yardNetwork)
            .withLabel("com.docker.compose.project", project)
            .withLabel("com.docker.compose.service", "web"));
  }

  /**
   * Starts a real container called {@code name} that listens on port 80, published on a free host
   * port when {@code publish} is true. It is removed after the test.
   */
  private GenericContainer<?> startRealContainer(String name, boolean publish) {
    GenericContainer<?> container = TestcontainersConfiguration.listener(name, 80);
    if (publish) {
      container.withExposedPorts(80);
    }
    return start(container);
  }

  /**
   * Where each Instance of the remote Workstation's {@code service} is, as this Workstation reads
   * it.
   */
  private List<String> remoteInstanceAddresses(String service) {
    return get("/api/services/" + RemoteWorkstation.NAME + "/" + service)
        .getBody()
        .get("instances")
        .valueStream()
        .map(instance -> instance.get("host").asString() + ":" + instance.get("port").asInt())
        .toList();
  }

  private static String instanceRow(String instances, String value) {
    int at = instances.indexOf(value, instances.indexOf("<tbody>"));
    assertThat(at).as("Instance row containing %s", value).isNotNegative();
    return instances.substring(instances.lastIndexOf("<tr>", at), instances.indexOf("</tr>", at));
  }

  @Test
  void dashboardCatchesUpOnStartupWithContainersThatChangedWhileItWasNotRunning() {
    String started = newServiceName("started");
    String stopped = newServiceName("stopped");
    String startedName = newContainerName();
    String stoppedName = newContainerName();
    GenericContainer<?> stopping = startRealContainer(stoppedName, false);
    remote.bind(started, startedName, 80);
    remote.bind(stopped, stoppedName, 80);
    await().atMost(FOLLOW_TIMEOUT).until(() -> remoteInstanceAddresses(stopped).size() == 1);

    GenericContainer<?>[] starting = new GenericContainer<?>[1];
    remote.whileDashboardIsStopped(() -> {
      stopping.stop();
      starting[0] = startRealContainer(startedName, false);
    });

    await()
        .atMost(FOLLOW_TIMEOUT)
        .untilAsserted(() -> {
          assertThat(remoteInstanceAddresses(started))
              .containsExactly(containerAddress(starting[0]) + ":80");
          assertThat(remoteInstanceAddresses(stopped)).isEmpty();
        });
  }

  @Test
  void remoteBoundInstanceShowsItsContainerAndPortsFromItsHomeWorkstation() {
    String web = newServiceName("web");
    String name = newContainerName();
    GenericContainer<?> container = startRealContainer(name, true);
    remote.bind(web, name, 80);
    String path = "/services/" + RemoteWorkstation.NAME + "/" + web;

    await()
        .atMost(FOLLOW_TIMEOUT)
        .untilAsserted(() -> {
          String instances = div(getPage(path).getBody(), "id=\"instances\"");
          assertThat(instances)
              .contains("<th>Container</th><th>Container Port</th><th>Host Port</th>");
          assertThat(instanceRow(instances, name))
              .contains(
                  "<td>" + name + "</td><td>80</td><td>" + container.getMappedPort(80) + "</td>");
        });
    JsonNode instance =
        get("/api/services/" + RemoteWorkstation.NAME + "/" + web)
            .getBody()
            .get("instances")
            .get(0);
    assertThat(instance.get("container").asString()).isEqualTo(name);
    assertThat(instance.get("containerPort").asInt()).isEqualTo(80);
    assertThat(instance.get("hostPort").asInt()).isEqualTo(container.getMappedPort(80));
  }
}
