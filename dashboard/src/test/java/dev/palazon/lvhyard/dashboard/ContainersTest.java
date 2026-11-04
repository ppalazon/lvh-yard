package dev.palazon.lvhyard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HealthCheck;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
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

  @Test
  void importGivesAPlainContainerAServiceOfItsNameBoundByNameAndPublishedLocally() {
    String name = newContainerName();
    start(onePort(name));

    ResponseEntity<JsonNode> response = post(importPath(name), Map.of());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode service = response.getBody();
    assertThat(service.get("name").asString()).isEqualTo(name);
    assertThat(service.get("publication").get("scope").asString()).isEqualTo("local");
    assertThat(service.get("containerBindings").get(0).get("id").asString())
        .isEqualTo(name + ":80");
    assertThat(listed(get(PATH).getBody(), name)).isEmpty();
  }

  @Test
  void importBindsAComposeContainerByProjectAndServiceIntoAServiceOfTheComposeServiceName() {
    String name = newContainerName();
    String project = newComposeProject();
    String web = newServiceName("web");
    start(
        onePort(name)
            .withLabel("com.docker.compose.project", project)
            .withLabel("com.docker.compose.service", web));

    JsonNode service = post(importPath(name), Map.of()).getBody();

    assertThat(service.get("name").asString()).isEqualTo(web);
    assertThat(service.get("containerBindings").get(0).get("id").asString())
        .isEqualTo("compose:" + project + ":" + web + ":80");
    assertThat(listed(get(PATH).getBody(), name)).isEmpty();
  }

  @Test
  void importIntoAnUnpublishedServiceKeepsItUnpublished() {
    String name = newContainerName();
    String web = newServiceName("web");
    start(onePort(name));
    register(web, Map.of("port", 8080));

    JsonNode service = post(importPath(name), Map.of("service", web)).getBody();

    assertThat(service.has("publication")).isFalse();
    assertThat(service.get("containerBindings").get(0).get("id").asString())
        .isEqualTo(name + ":80");
  }

  @Test
  void importIntoAPublishedServiceKeepsItsScope() {
    String name = newContainerName();
    String web = newServiceName("web");
    start(onePort(name));
    register(web, Map.of("port", 8080));
    put(publicationPath(web), Map.of("scope", "yard"));

    JsonNode service = post(importPath(name), Map.of("service", web)).getBody();

    assertThat(service.get("publication").get("scope").asString()).isEqualTo("yard");
  }

  @Test
  void composeServiceNameThatIsNotADnsLabelIsProposedCleaned() {
    String name = newContainerName();
    String project = newComposeProject();
    start(
        onePort(name)
            .withLabel("com.docker.compose.project", project)
            .withLabel("com.docker.compose.service", "My_App.v2"));

    assertThat(listed(get(PATH).getBody(), name).orElseThrow().get("service").asString())
        .isEqualTo("my-app-v2");
    JsonNode service = post(importPath(name), Map.of()).getBody();

    assertThat(service.get("name").asString()).isEqualTo("my-app-v2");
    assertThat(service.get("containerBindings").get(0).get("id").asString())
        .isEqualTo("compose:" + project + ":My_App.v2:80");
  }

  @Test
  void importWithAGivenNameThatIsNotADnsLabelIsRefused() {
    String name = newContainerName();
    start(onePort(name));

    ResponseEntity<JsonNode> response = post(importPath(name), Map.of("service", "my_app"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().get("detail").asString()).contains("my_app");
    assertThat(listed(get(PATH).getBody(), name)).isPresent();
  }

  @Test
  void importOfAContainerWithSeveralPortsWithoutAPortIsRefusedWithThePorts() {
    String name = newContainerName();
    start(twoPorts(name));

    ResponseEntity<JsonNode> response = post(importPath(name), Map.of());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().get("detail").asString()).contains("80, 9000");
    assertThat(listed(get(PATH).getBody(), name)).isPresent();
  }

  @Test
  void importOfAContainerWithSeveralPortsBindsTheChosenPort() {
    String name = newContainerName();
    start(twoPorts(name));

    JsonNode service = post(importPath(name), Map.of("port", 9000)).getBody();

    assertThat(service.get("containerBindings").get(0).get("id").asString())
        .isEqualTo(name + ":9000");
  }

  @Test
  void importOfAContainerWithNoExposedPortNeedsAPort() {
    String name = newContainerName();
    start(listener(name, command -> {}));

    ResponseEntity<JsonNode> refused = post(importPath(name), Map.of());
    JsonNode service = post(importPath(name), Map.of("port", 80)).getBody();

    assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(refused.getBody().get("detail").asString()).contains("no exposed port");
    assertThat(service.get("containerBindings").get(0).get("id").asString())
        .isEqualTo(name + ":80");
  }

  @Test
  void importWithPublishFalseLeavesANewServiceUnpublished() {
    String name = newContainerName();
    start(onePort(name));

    JsonNode service = post(importPath(name), Map.of("publish", false)).getBody();

    assertThat(service.get("name").asString()).isEqualTo(name);
    assertThat(service.has("publication")).isFalse();
    assertThat(service.get("containerBindings")).hasSize(1);
  }

  @Test
  void importIntoAYardScopedServiceWarnsAboutAnUnpublishedContainerPort() {
    String name = newContainerName();
    String web = newServiceName("web");
    start(onePort(name));
    register(web, Map.of("port", 8080));
    put(publicationPath(web), Map.of("scope", "yard"));

    JsonNode service = post(importPath(name), Map.of("service", web)).getBody();

    assertThat(service.get("warnings").valueStream().map(warning -> warning.get("kind").asString()))
        .containsExactly("unpublished-container-port");
  }

  @Test
  void importWithACoreServiceNameIsRefused() {
    String name = newContainerName();
    start(onePort(name));

    ResponseEntity<JsonNode> response = post(importPath(name), Map.of("service", "traefik"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().get("detail").asString()).contains("Core Service");
  }

  @Test
  void importOfAContainerThatDoesNotRunIsNotFound() {
    ResponseEntity<JsonNode> response = post(importPath(newContainerName()), Map.of());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void pageImportsAOnePortContainerInOneClickAndShowsItsService() {
    String name = newContainerName();
    start(onePort(name));
    assertThat(row(getPage("/containers").getBody(), name))
        .contains("action=\"/containers/" + name + "/import\"");

    ResponseEntity<String> response = postForm("/containers/" + name + "/import", Map.of());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER);
    assertThat(response.getHeaders().getLocation()).hasPath("/services/ws-test/" + name);
    assertThat(getPage("/containers").getBody()).doesNotContain(name);
  }

  @Test
  void pageShowsWhyAnImportFailsNextToTheContainer() {
    String name = newContainerName();
    start(
        onePort(name)
            .withLabel("com.docker.compose.project", newComposeProject())
            .withLabel("com.docker.compose.service", "traefik"));

    ResponseEntity<String> response = postForm("/containers/" + name + "/import", Map.of());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(row(response.getBody(), name)).contains("role=\"alert\"").contains("Core Service");
  }

  @Test
  void pageLetsTheUserChooseTheNameAndPortOfEachContainer() {
    String one = newContainerName();
    String two = newContainerName();
    start(onePort(one));
    start(twoPorts(two));

    String page = getPage("/containers").getBody();

    assertThat(row(page, one))
        .contains("action=\"/containers/" + one + "/import\"")
        .contains("href=\"/containers/" + one + "/import\"");
    assertThat(row(page, two))
        .doesNotContain("action=")
        .contains("href=\"/containers/" + two + "/import\"");
  }

  @Test
  void importFormProposesTheNameAndListsThePorts() {
    String name = newContainerName();
    start(twoPorts(name));

    ResponseEntity<String> response = getPage("/containers/" + name + "/import");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(openingTag(response.getBody(), "name=\"name\"")).contains("value=\"" + name + "\"");
    assertThat(response.getBody()).contains("name=\"port\"").contains("80, 9000");
  }

  @Test
  void importFormOfAContainerThatIsNotListedIsNotFound() {
    assertThat(getPage("/containers/" + newContainerName() + "/import").getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void importFormImportsWithTheChosenNameAndPort() {
    String name = newContainerName();
    String web = newServiceName("web");
    start(twoPorts(name));

    ResponseEntity<String> response =
        postForm("/containers/" + name + "/import", Map.of("name", web, "port", "9000"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER);
    assertThat(response.getHeaders().getLocation()).hasPath("/services/ws-test/" + web);
    assertThat(get(servicePath(web)).getBody().get("containerBindings").get(0).get("id").asString())
        .isEqualTo(name + ":9000");
  }

  @Test
  void importFormShowsTheErrorAtItsFieldAndKeepsWhatWasTyped() {
    String name = newContainerName();
    start(twoPorts(name));

    ResponseEntity<String> response =
        postForm("/containers/" + name + "/import", Map.of("name", "my_app", "port", "9000"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(openingTag(response.getBody(), "name=\"name\""))
        .contains("aria-invalid=\"true\"")
        .contains("value=\"my_app\"");
    assertThat(openingTag(response.getBody(), "name=\"port\"")).contains("value=\"9000\"");
  }

  @Test
  void importFormWithoutAPortForAContainerWithSeveralShowsThePortsAtThePortField() {
    String name = newContainerName();
    start(twoPorts(name));

    ResponseEntity<String> response =
        postForm("/containers/" + name + "/import", Map.of("name", name, "port", ""));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(openingTag(response.getBody(), "name=\"port\"")).contains("aria-invalid=\"true\"");
    assertThat(response.getBody()).contains("give one of 80, 9000");
  }

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

  private static String importPath(String container) {
    return PATH + "/" + container + "/import";
  }

  /** A container called {@code name} whose one port is 80. */
  private static GenericContainer<?> onePort(String name) {
    return listener(name, command -> command.withExposedPorts(ExposedPort.tcp(80)));
  }

  /** A container called {@code name} whose ports are 80 and 9000. */
  private static GenericContainer<?> twoPorts(String name) {
    return listener(
        name, command -> command.withExposedPorts(ExposedPort.tcp(80), ExposedPort.tcp(9000)));
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
