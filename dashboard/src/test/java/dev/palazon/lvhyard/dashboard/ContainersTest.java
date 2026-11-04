package dev.palazon.lvhyard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HealthCheck;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;

/**
 * The running containers on this Workstation that no Container Binding covers yet. Other tests, and
 * the developer, run containers on the same Docker, so each test looks only at its own.
 */
class ContainersTest extends DashboardTest {

  private static final String PATH = "/api/workstation/containers";

  /** Busybox has {@code nc}, and, unlike the Consul image, exposes no port of its own. */
  private static final DockerImageName BUSYBOX = DockerImageName.parse("busybox:1.37");

  @Test
  void runningContainerIsListedWithItsContainerAndHostPorts() {
    String name = newContainerName();
    GenericContainer<?> container =
        start(
            listener(
                    name,
                    command ->
                        command.withExposedPorts(
                            Stream.concat(
                                    Arrays.stream(command.getExposedPorts()),
                                    Stream.of(ExposedPort.tcp(9000)))
                                .toList()))
                .withExposedPorts(80));

    ResponseEntity<JsonNode> response = get(PATH);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode listed = listed(response.getBody(), name).orElseThrow();
    assertThat(listed.has("composeProject")).isFalse();
    assertThat(listed.has("health")).isFalse();
    assertThat(listed.get("ports").valueStream().map(JsonNode::toString))
        .containsExactly(
            "{\"containerPort\":80,\"hostPort\":" + container.getMappedPort(80) + "}",
            "{\"containerPort\":9000}");
  }

  @Test
  void composeContainerIsListedWithItsProjectAndService() {
    String name = newContainerName();
    String project = newComposeProject();
    start(
        listener(name, command -> {})
            .withLabel("com.docker.compose.project", project)
            .withLabel("com.docker.compose.service", "web"));

    JsonNode listed = listed(get(PATH).getBody(), name).orElseThrow();

    assertThat(listed.get("composeProject").asString()).isEqualTo(project);
    assertThat(listed.get("composeService").asString()).isEqualTo("web");
  }

  @Test
  void containerWithAHealthcheckIsListedWithItsHealth() {
    String name = newContainerName();
    start(
        listener(
            name,
            command ->
                command.withHealthcheck(
                    new HealthCheck()
                        .withTest(List.of("CMD-SHELL", "true"))
                        .withInterval(Duration.ofMillis(500).toNanos())
                        .withStartInterval(Duration.ofMillis(500).toNanos()))));

    await()
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(() ->
            assertThat(listed(get(PATH).getBody(), name).orElseThrow().get("health").asString())
                .isEqualTo("healthy"));
  }

  @Test
  void containerThatABindingFollowsByNameIsLeftOut() {
    String name = newContainerName();
    start(listener(name, command -> {}));
    assertThat(listed(get(PATH).getBody(), name)).isPresent();

    bind(newServiceName("web"), name, 1234);

    assertThat(listed(get(PATH).getBody(), name)).isEmpty();
  }

  @Test
  void containerThatABindingFollowsByComposeServiceIsLeftOut() {
    String name = newContainerName();
    String project = newComposeProject();
    start(
        listener(name, command -> {})
            .withLabel("com.docker.compose.project", project)
            .withLabel("com.docker.compose.service", "web"));

    bindCompose(newServiceName("web"), project, "web", 1234);

    assertThat(listed(get(PATH).getBody(), name)).isEmpty();
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
  void pageGroupsComposeContainersByProjectAndListsOthersApart() {
    String plain = newContainerName();
    String composed = newContainerName();
    String project = newComposeProject();
    start(listener(plain, command -> command.withExposedPorts(ExposedPort.tcp(80))));
    start(
        listener(composed, command -> {})
            .withLabel("com.docker.compose.project", project)
            .withLabel("com.docker.compose.service", "api"));

    ResponseEntity<String> response = getPage("/containers");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    String page = response.getBody();
    String group = div(page, "data-compose-project=\"" + project + "\"");
    assertThat(group).contains(project).contains(composed).contains("api");
    assertThat(group).doesNotContain(plain);
    assertThat(div(page, "data-compose-project=\"\"")).contains(plain).contains("80");
  }

  @Test
  void pageRefreshesItsListByItself() {
    assertThat(openingTag(getPage("/containers").getBody(), "id=\"containers\""))
        .contains("hx-get=\"/containers\"")
        .contains("hx-trigger=\"every 5s\"");
  }

  @Test
  void catalogLinksToThePage() {
    assertThat(getPage("/").getBody()).contains("href=\"/containers\"");
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

  /** {@code name} in the list, if it is there. */
  private static Optional<JsonNode> listed(JsonNode containers, String name) {
    return containers
        .valueStream()
        .filter(container -> container.get("name").asString().equals(name))
        .findFirst();
  }

  /**
   * A container called {@code name} that accepts TCP connections on port 80, with what {@code more}
   * adds. It waits for nothing, as its ports may be unpublished.
   */
  private static GenericContainer<?> listener(String name, Consumer<CreateContainerCmd> more) {
    return new GenericContainer<>(BUSYBOX)
        .withCreateContainerCmdModifier(command -> {
          command
              .withName(name)
              .withEntrypoint("sh", "-c", "while true; do nc -l -p 80 >/dev/null; done")
              .withCmd();
          more.accept(command);
        });
  }
}
