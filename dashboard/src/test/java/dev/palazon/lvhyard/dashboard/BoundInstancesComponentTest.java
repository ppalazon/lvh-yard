package dev.palazon.lvhyard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.palazon.lvhyard.dashboard.docker.Docker.Container;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * Bound Instances that need real containers: those of the other Workstation, whose own dashboard
 * follows the real Docker, and those whose checks Consul runs.
 */
class BoundInstancesComponentTest extends DashboardComponentTest {

  private Container startComposeOnYard(String name, String project, int port) {
    return startComposeContainer(name, project, "web", port);
  }

  @Test
  void composeBindingSummarizesItsContainersAndKeepsTheirDetailsInTheInstancesCard() {
    String web = newServiceName("web");
    String project = newComposeProject();
    String firstName = newContainerName();
    String secondName = newContainerName();
    String failingName = newContainerName();
    startComposeOnYard(firstName, project, 80);
    startComposeOnYard(secondName, project, 80);
    startComposeOnYard(failingName, project, 81);
    bindCompose(web, project, "web", 80);
    await()
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(() ->
            assertThat(div(getPage("/services/ws-test/" + web).getBody(), "id=\"sources\""))
                .contains("3 containers, 2 passing"));

    String page = getPage("/services/ws-test/" + web).getBody();

    assertThat(page)
        .contains("Compose service web of project " + project)
        .contains("Bound to Compose service web of project " + project + ", port 80.");
    assertThat(div(page, "id=\"sources\""))
        .contains("3 containers, 2 passing")
        .contains("<td>" + firstName + "</td><td>80</td><td>Not published</td>")
        .contains("<td>" + secondName + "</td><td>80</td><td>Not published</td>")
        .contains("<td>" + failingName + "</td><td>80</td><td>Not published</td>");
  }

  private static final Duration FOLLOW_TIMEOUT = Duration.ofSeconds(15);

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
    Container stopping = startContainer(stoppedName, 80, false);
    remote.bind(started, startedName, 80);
    remote.bind(stopped, stoppedName, 80);
    await().atMost(FOLLOW_TIMEOUT).until(() -> remoteInstanceAddresses(stopped).size() == 1);

    Container[] starting = new Container[1];
    remote.whileDashboardIsStopped(() -> {
      docker.remove(stoppedName);
      starting[0] = startContainer(startedName, 80, false);
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
    Container container = startContainer(name, 80, true);
    remote.bind(web, name, 80);
    String path = "/services/" + RemoteWorkstation.NAME + "/" + web;

    await()
        .atMost(FOLLOW_TIMEOUT)
        .untilAsserted(() -> {
          String instances = div(getPage(path).getBody(), "id=\"sources\"");
          assertThat(instances)
              .contains("<th>Container</th><th>Container Port</th><th>Host Port</th>");
          assertThat(instanceRow(instances, "<td>" + name + "</td>"))
              .contains("<td>" + name + "</td><td>80</td><td>" + hostPort(container, 80) + "</td>");
        });
    JsonNode instance =
        get("/api/services/" + RemoteWorkstation.NAME + "/" + web)
            .getBody()
            .get("instances")
            .get(0);
    assertThat(instance.get("container").asString()).isEqualTo(name);
    assertThat(instance.get("containerPort").asInt()).isEqualTo(80);
    assertThat(instance.get("hostPort").asInt()).isEqualTo(hostPort(container, 80));
  }
}
