package dev.palazon.lvhyard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.palazon.lvhyard.dashboard.docker.Docker.Container;
import java.time.Duration;
import org.junit.jupiter.api.Test;

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
    Container first = startComposeOnYard(firstName, project, 80);
    startComposeOnYard(secondName, project, 80);
    startComposeOnYard(failingName, project, 81);
    bindCompose(web, project, "web", 80);
    await()
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(() ->
            assertThat(
                    div(getPage("/services/ws-test/" + web).getBody(), "id=\"container-bindings\""))
                .contains("3 containers, 2 passing"));

    String page = getPage("/services/ws-test/" + web).getBody();

    assertThat(page)
        .contains("Compose service web of project " + project)
        .contains("Bound to Compose service web of project " + project + ", port 80.");
    assertThat(div(page, "id=\"container-bindings\""))
        .contains("3 containers, 2 passing")
        .doesNotContain(containerAddress(first) + ":80");
    assertThat(div(page, "id=\"instances\""))
        .contains("<td>" + firstName + "</td><td>80</td><td>Not published</td>")
        .contains("<td>" + secondName + "</td><td>80</td><td>Not published</td>")
        .contains("<td>" + failingName + "</td><td>80</td><td>Not published</td>");
  }
}
