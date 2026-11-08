package dev.palazon.lvhyard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import dev.palazon.lvhyard.dashboard.docker.FakeDocker;
import dev.palazon.lvhyard.dashboard.docker.FakeDocker.Spec;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/**
 * The running containers on this Workstation that no Container Binding covers yet. Other tests, and
 * the developer, run containers on the same Docker, so each test looks only at its own.
 */
class ContainersIT extends DashboardIT {

  private static final String PATH = "/api/workstation/containers";

  /** The table row of {@code container} on the Containers page. */
  private static String row(String page, String container) {
    int at = page.indexOf("data-container=\"" + container + "\"");
    assertThat(at).as("row of " + container).isNotNegative();
    int start = page.lastIndexOf("<tr", at);
    return page.substring(start, page.indexOf("</tr>", at) + "</tr>".length());
  }

  /** {@code name} in the list, if it is there. */
  private static Optional<JsonNode> listed(JsonNode containers, String name) {
    return containers
        .valueStream()
        .filter(container -> container.get("name").asString().equals(name))
        .findFirst();
  }

  private static String registerPath(String container) {
    return PATH + "/" + container + "/register";
  }

  /** A container called {@code name} whose one port is 80. */
  /** A container called {@code name} whose port 80 is not published. */
  private static Spec onePort(String name) {
    return FakeDocker.container(name).port(80);
  }

  /** A container called {@code name} whose ports are 80 and 9000. */
  private static Spec twoPorts(String name) {
    return FakeDocker.container(name).port(80).port(9000);
  }

  private static String rowOf(String page, String container) {
    int at = page.indexOf(">" + container + "<");
    assertThat(at).as("a row for %s", container).isNotNegative();
    return page.substring(page.lastIndexOf("<tr", at), page.indexOf("</tr>", at));
  }

  @Test
  void dockerThatCannotBeReadIsAProblemThatSaysWhy() {
    remote.whileDashboardRunsWith(
        () -> {
          ResponseEntity<JsonNode> response = remote.get(PATH);
          assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
          assertThat(response.getBody().get("detail").asString())
              .contains("No Docker socket at /nowhere/docker.sock");
        },
        "--lvh-yard.docker.host=unix:///nowhere/docker.sock");
  }

  @Test
  void pageSaysWhyItCannotReadDocker() {
    remote.whileDashboardRunsWith(
        () ->
            assertThat(remote.page("/containers"))
                .contains("id=\"containers\"")
                .contains("No Docker socket at /nowhere/docker.sock"),
        "--lvh-yard.docker.host=unix:///nowhere/docker.sock");
  }
}
