package dev.palazon.lvhyard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;

import dev.palazon.lvhyard.dashboard.docker.Docker.Container;
import dev.palazon.lvhyard.dashboard.docker.Docker.Port;
import dev.palazon.lvhyard.dashboard.docker.FakeDocker;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/**
 * Every container on this Workstation, as the container list API gives it. Other tests, and the
 * developer, run containers on the same Docker, so each test looks only at its own.
 */
class ContainersComponentTest extends DashboardComponentTest {

  private static final String PATH = "/api/workstation/containers";

  /** {@code name} in the list, if it is there. */
  private static Optional<JsonNode> listed(JsonNode containers, String name) {
    return containers
        .valueStream()
        .filter(container -> container.get("name").asString().equals(name))
        .findFirst();
  }

  private JsonNode listed(String name) {
    return listed(get(PATH).getBody(), name)
        .orElseThrow(() -> new AssertionError("The list has no container " + name));
  }

  @Test
  void runningContainerIsListedWithItsLoopbackMappingAndUnpublishedPorts() {
    String name = newContainerName();
    Container container = start(FakeDocker.container(name).port(9000).publishedOn("127.0.0.1", 80));

    ResponseEntity<JsonNode> response = get(PATH);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode listed = listed(response.getBody(), name).orElseThrow();
    assertThat(listed.get("running").asBoolean()).isTrue();
    assertThat(listed.has("composeProject")).isFalse();
    assertThat(listed.has("health")).isFalse();
    assertThat(listed.get("followedBy")).isEmpty();
    JsonNode ports = listed.get("ports");
    assertThat(ports).hasSize(2);
    assertThat(ports.get(0).get("containerPort").asInt()).isEqualTo(80);
    assertThat(ports.get(0).get("hostPort").asInt()).isEqualTo(hostPort(container, 80));
    assertThat(ports.get(0).get("hostIp").asString()).isEqualTo("127.0.0.1");
    assertThat(ports.get(1).get("containerPort").asInt()).isEqualTo(9000);
    assertThat(ports.get(1).has("hostPort")).isFalse();
    assertThat(ports.get(1).has("hostIp")).isFalse();
  }

  @Test
  void mappedPortsKeepEveryDockerBindAddress() {
    String name = newContainerName();
    Container container = start(FakeDocker.container(name).published(80));
    List<Port> bindings = container.published(80);

    JsonNode ports = listed(name).get("ports");

    assertThat(bindings).hasSize(2);
    assertThat(ports.valueStream())
        .extracting(
            port -> port.get("containerPort").asInt(),
            port -> port.get("hostPort").asInt(),
            port -> port.get("hostIp").asString())
        .containsExactlyInAnyOrderElementsOf(
            bindings.stream()
                .map(binding -> tuple(80, binding.publicPort(), binding.ip()))
                .toList());
  }

  @Test
  void composeContainerIsListedWithItsProjectAndService() {
    String name = newContainerName();
    String project = newComposeProject();
    start(FakeDocker.container(name).compose(project, "web"));

    JsonNode listed = listed(name);

    assertThat(listed.get("composeProject").asString()).isEqualTo(project);
    assertThat(listed.get("composeService").asString()).isEqualTo("web");
  }

  @Test
  void containerWithAComposeProjectButNoComposeServiceIsListedWithNeither() {
    String name = newContainerName();
    start(FakeDocker.container(name).label("com.docker.compose.project", newComposeProject()));

    JsonNode listed = listed(name);

    assertThat(listed.has("composeProject")).isFalse();
    assertThat(listed.has("composeService")).isFalse();
  }

  @Test
  void containerWithAHealthcheckIsListedWithItsHealth() {
    String name = newContainerName();
    start(FakeDocker.container(name).health("healthy"));

    await()
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(
            () -> assertThat(listed(name).get("health").asString()).isEqualTo("healthy"));
  }

  @Test
  void stoppedContainerIsListedAsNotRunning() {
    String name = newContainerName();
    Container container = start(FakeDocker.container(name).compose(newComposeProject(), "web"));

    stopContainer(container);

    JsonNode listed = listed(name);
    assertThat(listed.get("running").asBoolean()).isFalse();
    assertThat(listed.get("composeService").asString()).isEqualTo("web");
  }

  @Test
  void containerThatABindingFollowsByNameNamesItsService() {
    String name = newContainerName();
    start(FakeDocker.container(name));
    String web = newServiceName("web");

    bind(web, name, 1234);

    assertThat(listed(name).get("followedBy").valueStream().map(JsonNode::asString))
        .containsExactly(web);
  }

  @Test
  void containerThatSeveralServicesFollowNamesEachOfThem() {
    String name = newContainerName();
    start(FakeDocker.container(name));
    String web = newServiceName("web");
    String api = newServiceName("api");

    bind(web, name, 80);
    bind(api, name, 9000);

    assertThat(listed(name).get("followedBy").valueStream().map(JsonNode::asString))
        .containsExactlyInAnyOrder(web, api);
  }

  @Test
  void stoppedContainerThatABindingFollowsByComposeServiceNamesItsService() {
    String name = newContainerName();
    String project = newComposeProject();
    Container container = start(FakeDocker.container(name).compose(project, "web"));
    stopContainer(container);
    String web = newServiceName("web");

    bindCompose(web, project, "web", 1234);

    assertThat(listed(name).get("followedBy").valueStream().map(JsonNode::asString))
        .containsExactly(web);
  }

  @Test
  void composeServiceNameThatCleansToNothingProposesTheContainerName() {
    String name = newContainerName();
    start(FakeDocker.container(name).compose(newComposeProject(), "___"));

    assertThat(listed(name).get("service").asString()).isEqualTo(name);
  }

  @Test
  void startingAndStoppingContainerEmitsContainersChangedOverSseStream() throws Exception {
    try (EventStreamClient client = new EventStreamClient(port)) {
      String name = newContainerName();
      Container container = start(FakeDocker.container(name).port(80));

      await()
          .atMost(Duration.ofSeconds(10))
          .until(() -> client.lines.contains("event:containers-changed"));

      client.lines.clear();
      stopContainer(container);

      await()
          .atMost(Duration.ofSeconds(10))
          .until(() -> client.lines.contains("event:containers-changed"));
    }
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
}
