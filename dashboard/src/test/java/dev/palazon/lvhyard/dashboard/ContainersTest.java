package dev.palazon.lvhyard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;

import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HealthCheck;
import com.github.dockerjava.api.model.Ports;
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
  void runningContainerIsListedWithItsLoopbackMappingAndUnpublishedPorts() {
    String name = newContainerName();
    GenericContainer<?> container =
        start(
            listener(
                    name,
                    command ->
                        command
                            .withExposedPorts(
                                Stream.concat(
                                        Arrays.stream(command.getExposedPorts()),
                                        Stream.of(ExposedPort.tcp(9000)))
                                    .toList())
                            .getHostConfig()
                            .withPortBindings(
                                new Ports(
                                    ExposedPort.tcp(80),
                                    Ports.Binding.bindIpAndPort("127.0.0.1", 0))))
                .withExposedPorts(80));

    ResponseEntity<JsonNode> response = get(PATH);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode listed = listed(response.getBody(), name).orElseThrow();
    assertThat(listed.has("composeProject")).isFalse();
    assertThat(listed.has("health")).isFalse();
    JsonNode ports = listed.get("ports");
    assertThat(ports).hasSize(2);
    assertThat(ports.get(0).get("containerPort").asInt()).isEqualTo(80);
    assertThat(ports.get(0).get("hostPort").asInt()).isEqualTo(container.getMappedPort(80));
    assertThat(ports.get(0).get("hostIp").asString()).isEqualTo("127.0.0.1");
    assertThat(ports.get(1).get("containerPort").asInt()).isEqualTo(9000);
    assertThat(ports.get(1).has("hostPort")).isFalse();
    assertThat(ports.get(1).has("hostIp")).isFalse();
  }

  @Test
  void mappedPortsKeepEveryDockerBindAddressWithoutRepeatingPagePorts() {
    String name = newContainerName();
    GenericContainer<?> container = start(onePort(name).withExposedPorts(80));
    Ports.Binding[] bindings =
        container
            .getContainerInfo()
            .getNetworkSettings()
            .getPorts()
            .getBindings()
            .get(ExposedPort.tcp(80));

    JsonNode ports = listed(get(PATH).getBody(), name).orElseThrow().get("ports");

    assertThat(bindings).isNotEmpty();
    assertThat(ports.valueStream())
        .extracting(
            port -> port.get("containerPort").asInt(),
            port -> port.get("hostPort").asInt(),
            port -> port.get("hostIp").asString())
        .containsExactlyInAnyOrderElementsOf(
            Arrays.stream(bindings)
                .map(binding ->
                    tuple(80, Integer.parseInt(binding.getHostPortSpec()), binding.getHostIp()))
                .toList());
    String row = row(getPage("/containers").getBody(), name);
    // Docker publishes the port on 0.0.0.0 and on ::, and the page shows it once.
    for (Ports.Binding binding : bindings) {
      assertThat(row).containsOnlyOnce("host:" + binding.getHostPortSpec() + " -&gt; 80");
    }
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
    assertThat(rowOf(getPage("/containers").getBody(), name)).contains(badge("success", "healthy"));
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
  void pageShowsOneComposeServiceRowWithContainerPortsAndHealthCounts() {
    String project = newComposeProject();
    String healthy = newContainerName();
    String unhealthy = newContainerName();
    String unchecked = newContainerName();
    String starting = newContainerName();
    GenericContainer<?> first =
        start(
            onePort(healthy)
                .withExposedPorts(80)
                .withLabel("com.docker.compose.project", project)
                .withLabel("com.docker.compose.service", "api")
                .withCreateContainerCmdModifier(command ->
                    command.withHealthcheck(
                        new HealthCheck()
                            .withTest(List.of("CMD-SHELL", "true"))
                            .withInterval(Duration.ofMillis(500).toNanos()))));
    GenericContainer<?> second =
        start(
            twoPorts(unhealthy)
                .withExposedPorts(80)
                .withLabel("com.docker.compose.project", project)
                .withLabel("com.docker.compose.service", "api")
                .withCreateContainerCmdModifier(command ->
                    command.withHealthcheck(
                        new HealthCheck()
                            .withTest(List.of("CMD-SHELL", "false"))
                            .withRetries(1)
                            .withInterval(Duration.ofMillis(500).toNanos()))));
    start(
        onePort(unchecked)
            .withLabel("com.docker.compose.project", project)
            .withLabel("com.docker.compose.service", "api"));
    start(
        onePort(starting)
            .withLabel("com.docker.compose.project", project)
            .withLabel("com.docker.compose.service", "api")
            .withCreateContainerCmdModifier(command ->
                command.withHealthcheck(
                    new HealthCheck()
                        .withTest(List.of("CMD-SHELL", "false"))
                        .withStartPeriod(Duration.ofMinutes(5).toNanos())
                        .withInterval(Duration.ofMillis(500).toNanos()))));
    await()
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(() -> {
          JsonNode containers = get(PATH).getBody();
          assertThat(listed(containers, healthy).orElseThrow().get("health").asString())
              .isEqualTo("healthy");
          assertThat(listed(containers, unhealthy).orElseThrow().get("health").asString())
              .isEqualTo("unhealthy");
        });

    String group =
        div(getPage("/containers").getBody(), "data-compose-project=\"" + project + "\"");

    assertThat(group).containsOnlyOnce(">api<").contains("4 containers");
    assertThat(group)
        .contains("host:" + first.getMappedPort(80) + " -&gt; 80")
        .contains("host:" + second.getMappedPort(80) + " -&gt; 80")
        .containsOnlyOnce("80 not published")
        .containsOnlyOnce("9000 not published");
    assertThat(group).contains("1 healthy", "1 unhealthy", "1 starting", "1 without HEALTHCHECK");
  }

  @Test
  void composeRegistrationOffersThePortsOfEveryContainerAndBindsTheWholeService() {
    String prefix = newContainerName();
    String first = prefix + "-a";
    String second = prefix + "-b";
    String project = newComposeProject();
    String web = newServiceName("web");
    start(
        onePort(first)
            .withLabel("com.docker.compose.project", project)
            .withLabel("com.docker.compose.service", web));
    start(
        twoPorts(second)
            .withLabel("com.docker.compose.project", project)
            .withLabel("com.docker.compose.service", web));

    String group =
        div(getPage("/containers").getBody(), "data-compose-project=\"" + project + "\"");
    assertThat(group).contains("Choose port").doesNotContain("action=");
    String form = getPage("/containers/" + first + "/register").getBody();
    assertThat(form).contains("80, 9000");
    assertThat(openingTag(form, "name=\"port\"")).contains("value=\"\"");

    ResponseEntity<String> response =
        postForm("/containers/" + first + "/register", Map.of("name", web, "port", "9000"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER);
    assertThat(response.getHeaders().getLocation()).hasPath("/services/ws-test/" + web);
    await()
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(() -> {
          JsonNode service = get(servicePath(web)).getBody();
          assertThat(service.get("instances")).hasSize(2);
          assertThat(service.get("containerBindings").get(0).get("id").asString())
              .isEqualTo("compose:" + project + ":" + web + ":9000");
        });
    assertThat(getPage("/containers").getBody())
        .doesNotContain("data-compose-project=\"" + project + "\"");
  }

  @Test
  void oneClickComposeRegistrationUsesTheGroupsOnlyPortEvenWhenTheFirstContainerListsNone() {
    String prefix = newContainerName();
    String first = prefix + "-a";
    String second = prefix + "-b";
    String project = newComposeProject();
    String web = newServiceName("web");
    start(
        listener(first, command -> {})
            .withLabel("com.docker.compose.project", project)
            .withLabel("com.docker.compose.service", web));
    start(
        onePort(second)
            .withLabel("com.docker.compose.project", project)
            .withLabel("com.docker.compose.service", web));

    String group =
        div(getPage("/containers").getBody(), "data-compose-project=\"" + project + "\"");
    assertThat(group).contains(">Register</button>");

    ResponseEntity<String> response =
        postForm("/containers/" + first + "/register", Map.of("port", "80"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER);
    assertThat(response.getHeaders().getLocation()).hasPath("/containers");
    await()
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(
            () -> assertThat(get(servicePath(web)).getBody().get("instances")).hasSize(2));
    assertThat(getPage("/containers").getBody())
        .doesNotContain("data-compose-project=\"" + project + "\"");
  }

  @Test
  void composeServiceRowsStaySeparateAcrossServicesAndProjects() {
    String first = newComposeProject();
    String second = newComposeProject();
    for (String service : List.of("web", "api")) {
      start(
          onePort(newContainerName())
              .withLabel("com.docker.compose.project", first)
              .withLabel("com.docker.compose.service", service));
    }
    start(
        twoPorts(newContainerName())
            .withLabel("com.docker.compose.project", second)
            .withLabel("com.docker.compose.service", "web"));

    String page = getPage("/containers").getBody();
    String firstGroup = div(page, "data-compose-project=\"" + first + "\"");
    String secondGroup = div(page, "data-compose-project=\"" + second + "\"");

    assertThat(firstGroup).containsOnlyOnce(">web<").containsOnlyOnce(">api<");
    assertThat(firstGroup).doesNotContain(">9000 not published<", ">2 containers<");
    assertThat(secondGroup)
        .containsOnlyOnce(">web<")
        .contains(">9000 not published<", ">1 container<");
    assertThat(secondGroup).doesNotContain(">api<");
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
  void registerGivesAPlainContainerAServiceOfItsNameBoundByNameAndPublishedLocally() {
    String name = newContainerName();
    start(onePort(name));

    ResponseEntity<JsonNode> response = post(registerPath(name), Map.of());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode service = response.getBody();
    assertThat(service.get("name").asString()).isEqualTo(name);
    assertThat(service.get("publication").get("scope").asString()).isEqualTo("local");
    assertThat(service.get("containerBindings").get(0).get("id").asString())
        .isEqualTo(name + ":80");
    assertThat(listed(get(PATH).getBody(), name)).isEmpty();
  }

  @Test
  void registerBindsOnlyTheNamedComposeContainerIntoAServiceOfItsCleanedName() {
    String id = newContainerName().substring("yard-test-".length());
    String name = "Yard_Test." + id;
    String sibling = newContainerName();
    String project = newComposeProject();
    String web = newServiceName("web");
    start(
        onePort(name)
            .withLabel("com.docker.compose.project", project)
            .withLabel("com.docker.compose.service", web));
    start(
        onePort(sibling)
            .withLabel("com.docker.compose.project", project)
            .withLabel("com.docker.compose.service", web));
    assertThat(listed(get(PATH).getBody(), name).orElseThrow().get("service").asString())
        .isEqualTo(web);

    ResponseEntity<JsonNode> response = post(registerPath(name), Map.of());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode service = response.getBody();
    String serviceName = "yard-test-" + id;
    assertThat(service.get("name").asString()).isEqualTo(serviceName);
    assertThat(service.get("publication").get("scope").asString()).isEqualTo("local");
    assertThat(service.get("containerBindings")).hasSize(1);
    assertThat(service.get("containerBindings").get(0).get("id").asString())
        .isEqualTo(name + ":80");
    await()
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(() -> {
          JsonNode instances = get(servicePath(serviceName)).getBody().get("instances");
          assertThat(instances).hasSize(1);
          assertThat(instances.get(0).get("container").asString()).isEqualTo(name);
        });
    JsonNode unfollowed = get(PATH).getBody();
    assertThat(listed(unfollowed, name)).isEmpty();
    assertThat(listed(unfollowed, sibling)).isPresent();
  }

  @Test
  void registerIntoAnUnpublishedServiceKeepsItUnpublished() {
    String name = newContainerName();
    String web = newServiceName("web");
    start(onePort(name));
    register(web, Map.of("port", 8080));

    JsonNode service = post(registerPath(name), Map.of("service", web)).getBody();

    assertThat(service.has("publication")).isFalse();
    assertThat(service.get("containerBindings").get(0).get("id").asString())
        .isEqualTo(name + ":80");
  }

  @Test
  void registerIntoAPublishedServiceKeepsItsScope() {
    String name = newContainerName();
    String web = newServiceName("web");
    start(onePort(name));
    register(web, Map.of("port", 8080));
    put(publicationPath(web), Map.of("scope", "yard"));

    JsonNode service = post(registerPath(name), Map.of("service", web)).getBody();

    assertThat(service.get("publication").get("scope").asString()).isEqualTo("yard");
  }

  @Test
  void pageRegistersAComposeServiceWithItsCleanedProposedName() {
    String name = newContainerName();
    String project = newComposeProject();
    start(
        onePort(name)
            .withLabel("com.docker.compose.project", project)
            .withLabel("com.docker.compose.service", "My_App.v2"));

    assertThat(listed(get(PATH).getBody(), name).orElseThrow().get("service").asString())
        .isEqualTo("my-app-v2");
    ResponseEntity<String> response = postForm("/containers/" + name + "/register", Map.of());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER);
    JsonNode service = get(servicePath("my-app-v2")).getBody();
    assertThat(service.get("name").asString()).isEqualTo("my-app-v2");
    assertThat(service.get("containerBindings").get(0).get("id").asString())
        .isEqualTo("compose:" + project + ":My_App.v2:80");
  }

  @Test
  void registerWithAGivenNameThatIsNotADnsLabelIsRefused() {
    String name = newContainerName();
    start(onePort(name));

    ResponseEntity<JsonNode> response = post(registerPath(name), Map.of("service", "my_app"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().get("detail").asString()).contains("my_app");
    assertThat(listed(get(PATH).getBody(), name)).isPresent();
  }

  @Test
  void registerOfAContainerWithSeveralPortsWithoutAPortIsRefusedWithThePorts() {
    String name = newContainerName();
    start(twoPorts(name));

    ResponseEntity<JsonNode> response = post(registerPath(name), Map.of());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().get("detail").asString()).contains("80, 9000");
    assertThat(listed(get(PATH).getBody(), name)).isPresent();
  }

  @Test
  void registerOfAContainerWithSeveralPortsBindsTheChosenPort() {
    String name = newContainerName();
    start(twoPorts(name));

    JsonNode service = post(registerPath(name), Map.of("port", 9000)).getBody();

    assertThat(service.get("containerBindings").get(0).get("id").asString())
        .isEqualTo(name + ":9000");
  }

  @Test
  void registerOfAContainerWithNoExposedPortNeedsAPort() {
    String name = newContainerName();
    start(listener(name, command -> {}));

    ResponseEntity<JsonNode> refused = post(registerPath(name), Map.of());
    JsonNode service = post(registerPath(name), Map.of("port", 80)).getBody();

    assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(refused.getBody().get("detail").asString()).contains("no exposed port");
    assertThat(service.get("containerBindings").get(0).get("id").asString())
        .isEqualTo(name + ":80");
  }

  @Test
  void registerWithPublishFalseLeavesANewServiceUnpublished() {
    String name = newContainerName();
    start(onePort(name));

    JsonNode service = post(registerPath(name), Map.of("publish", false)).getBody();

    assertThat(service.get("name").asString()).isEqualTo(name);
    assertThat(service.has("publication")).isFalse();
    assertThat(service.get("containerBindings")).hasSize(1);
  }

  @Test
  void registerIntoAYardScopedServiceWarnsAboutAnUnpublishedContainerPort() {
    String name = newContainerName();
    String web = newServiceName("web");
    start(onePort(name));
    register(web, Map.of("port", 8080));
    put(publicationPath(web), Map.of("scope", "yard"));

    JsonNode service = post(registerPath(name), Map.of("service", web)).getBody();

    assertThat(service.get("warnings").valueStream().map(warning -> warning.get("kind").asString()))
        .containsExactly("unpublished-container-port");
  }

  @Test
  void registerOfAContainerThatDoesNotRunBindsItByNameIntoAServiceOfTheCleanedName() {
    String id = newContainerName().substring("yard-test-".length());
    String name = "Yard_Test." + id;

    ResponseEntity<JsonNode> response = post(registerPath(name), Map.of("port", 80));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode service = response.getBody();
    assertThat(service.get("name").asString()).isEqualTo("yard-test-" + id);
    assertThat(service.get("publication").get("scope").asString()).isEqualTo("local");
    assertThat(service.get("containerBindings").get(0).get("id").asString())
        .isEqualTo(name + ":80");
  }

  @Test
  void registerOfAContainerThatDoesNotRunUsesTheGivenServiceAndPublish() {
    String name = newContainerName();
    String web = newServiceName("web");

    JsonNode service =
        post(registerPath(name), Map.of("port", 8080, "service", web, "publish", false)).getBody();

    assertThat(service.get("name").asString()).isEqualTo(web);
    assertThat(service.has("publication")).isFalse();
    assertThat(service.get("containerBindings").get(0).get("id").asString())
        .isEqualTo(name + ":8080");
  }

  @Test
  void registerOfAContainerThatDoesNotRunWithoutAPortIsRefusedAtThePort() {
    String name = newContainerName();

    ResponseEntity<JsonNode> response = post(registerPath(name), Map.of());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().get("field").asString()).isEqualTo("port");
    assertThat(response.getBody().get("detail").asString()).contains(name).contains("port");
    assertThat(get(servicePath(name)).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void registerOfAContainerThatDoesNotRunButABindingFollowsIsRefused() {
    String name = newContainerName();
    String web = newServiceName("web");
    bind(web, name, 80);

    ResponseEntity<JsonNode> response = post(registerPath(name), Map.of("port", 9000));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody().get("detail").asString()).contains(name).contains(web);
    assertThat(get(servicePath(web)).getBody().get("containerBindings")).hasSize(1);
  }

  @Test
  void containerRegisteredBeforeItRunsGetsItsInstanceWhenItStarts() {
    String name = newContainerName();
    JsonNode registered = post(registerPath(name), Map.of("port", 80)).getBody();
    assertThat(registered.get("instances")).isEmpty();

    start(onePort(name));

    await()
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(
            () -> assertThat(get(servicePath(name)).getBody().get("instances")).hasSize(1));
  }

  @Test
  void pageRegistersAOnePortContainerInOneClickAndStaysOnTheListWithAConfirmation() {
    String name = newContainerName();
    start(onePort(name));
    assertThat(row(getPage("/containers").getBody(), name))
        .contains("action=\"/containers/" + name + "/register\"")
        .contains(">Register<")
        .doesNotContainIgnoringCase("import");

    ResponseEntity<String> response = postForm("/containers/" + name + "/register", Map.of());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER);
    assertThat(response.getHeaders().getLocation()).hasPath("/containers");
    String page = getPage(response.getHeaders().getLocation().toString()).getBody();
    assertThat(div(page, "id=\"containers\"")).doesNotContain(name);
    assertThat(openingTag(page, "id=\"containers\""))
        .contains("hx-trigger=\"containers-changed from:body, yard-changed from:body, every 30s\"");
    // One paragraph, so the text, the link and the period stay on one line.
    assertThat(div(page, "data-registered"))
        .contains(
            "<p>Registered in the Service <a class=\"underline\" href=\"/services/ws-test/"
                + name
                + "\">"
                + name
                + "</a>.</p>");
  }

  @Test
  void registerOfAContainerThatABindingFollowsIsRefused() {
    String name = newContainerName();
    String web = newServiceName("web");
    start(onePort(name));
    bind(web, name, 80);

    ResponseEntity<JsonNode> response = post(registerPath(name), Map.of("service", web));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody().get("detail").asString()).contains(name).contains(web);
    assertThat(get(servicePath(web)).getBody().get("containerBindings")).hasSize(1);
  }

  @Test
  void registerByNameOfAContainerThatAComposeBindingFollowsIsRefused() {
    String name = newContainerName();
    String project = newComposeProject();
    String web = newServiceName("web");
    start(
        onePort(name)
            .withLabel("com.docker.compose.project", project)
            .withLabel("com.docker.compose.service", "web"));
    bindCompose(web, project, "web", 80);

    ResponseEntity<JsonNode> response = post(registerPath(name), Map.of("port", 9000));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody().get("detail").asString()).contains(name).contains(web);
    assertThat(get(servicePath(web)).getBody().get("containerBindings")).hasSize(1);
  }

  @Test
  void containerWithAComposeProjectButNoComposeServiceIsRegisteredByItsName() {
    String name = newContainerName();
    start(onePort(name).withLabel("com.docker.compose.project", newComposeProject()));

    ResponseEntity<JsonNode> response = post(registerPath(name), Map.of());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().get("name").asString()).isEqualTo(name);
    assertThat(response.getBody().get("containerBindings").get(0).get("id").asString())
        .isEqualTo(name + ":80");
  }

  @Test
  void composeServiceNameThatCleansToNothingProposesTheContainerName() {
    String name = newContainerName();
    start(
        onePort(name)
            .withLabel("com.docker.compose.project", newComposeProject())
            .withLabel("com.docker.compose.service", "___"));

    assertThat(listed(get(PATH).getBody(), name).orElseThrow().get("service").asString())
        .isEqualTo(name);
  }

  @Test
  void pageShowsWhyARegisterFailsNextToTheContainer() {
    String name = newContainerName();
    start(
        onePort(name)
            .withLabel("com.docker.compose.project", newComposeProject())
            .withLabel("com.docker.compose.service", "consul"));

    ResponseEntity<String> response = postForm("/containers/" + name + "/register", Map.of());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(row(response.getBody(), name))
        .contains("role=\"alert\"")
        .contains("own entry in its catalog");
  }

  @Test
  void pageLetsTheUserChooseTheNameAndPortOfEachContainer() {
    String one = newContainerName();
    String two = newContainerName();
    start(onePort(one));
    start(twoPorts(two));

    String page = getPage("/containers").getBody();

    assertThat(row(page, one))
        .contains("action=\"/containers/" + one + "/register\"")
        .contains("href=\"/containers/" + one + "/register\"");
    assertThat(row(page, two))
        .doesNotContain("action=")
        .contains("href=\"/containers/" + two + "/register\"");
  }

  @Test
  void registerFormProposesTheNameAndListsThePorts() {
    String name = newContainerName();
    start(twoPorts(name));

    ResponseEntity<String> response = getPage("/containers/" + name + "/register");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(openingTag(response.getBody(), "name=\"name\"")).contains("value=\"" + name + "\"");
    assertThat(response.getBody()).contains("name=\"port\"").contains("80, 9000");
  }

  @Test
  void registerFormOfAContainerThatIsNotListedIsNotFound() {
    assertThat(getPage("/containers/" + newContainerName() + "/register").getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void registerFormRegistersWithTheChosenNameAndPort() {
    String name = newContainerName();
    String web = newServiceName("web");
    start(twoPorts(name));

    ResponseEntity<String> response =
        postForm("/containers/" + name + "/register", Map.of("name", web, "port", "9000"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER);
    assertThat(response.getHeaders().getLocation()).hasPath("/services/ws-test/" + web);
    assertThat(get(servicePath(web)).getBody().get("containerBindings").get(0).get("id").asString())
        .isEqualTo(name + ":9000");
  }

  @Test
  void registerFormShowsTheErrorAtItsFieldAndKeepsWhatWasTyped() {
    String name = newContainerName();
    start(twoPorts(name));

    ResponseEntity<String> response =
        postForm("/containers/" + name + "/register", Map.of("name", "my_app", "port", "9000"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(openingTag(response.getBody(), "name=\"name\""))
        .contains("aria-invalid=\"true\"")
        .contains("value=\"my_app\"");
    assertThat(openingTag(response.getBody(), "name=\"port\"")).contains("value=\"9000\"");
  }

  @Test
  void registerFormWithoutAPortForAContainerWithSeveralShowsThePortsAtThePortField() {
    String name = newContainerName();
    start(twoPorts(name));

    ResponseEntity<String> response =
        postForm("/containers/" + name + "/register", Map.of("name", name, "port", ""));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(openingTag(response.getBody(), "name=\"port\"")).contains("aria-invalid=\"true\"");
    assertThat(response.getBody()).contains("give one of 80, 9000");
  }

  @Test
  void bindLinkOpensTheBindingFormOnItsOwnPage() {
    assertFormPage("/containers", "/containers/new", "Bind a container", "/containers/bind");
    String page = getPage("/containers").getBody();
    assertThat(page.indexOf("href=\"/containers/new\""))
        .isLessThan(page.indexOf("id=\"containers\""));
  }

  @Test
  void bindingFormPublishesANewServiceByDefault() {
    String form = formTo(getPage("/containers/new").getBody(), "/containers/bind");

    // app.css shows the fields of the chosen kind by these attributes and ids.
    assertThat(openingTag(form, "action=\"/containers/bind\"")).contains("data-bind-form");
    assertThat(openingTag(form, "value=\"CONTAINER\""))
        .contains("type=\"radio\"")
        .contains("id=\"kind-container\"");
    assertThat(openingTag(form, "value=\"COMPOSE\""))
        .contains("type=\"radio\"")
        .contains("id=\"kind-compose\"");
    assertThat(form)
        .contains("Container name")
        .contains("Compose service")
        .contains("id=\"container\"")
        .contains("id=\"composeProject\"")
        .contains("id=\"composeService\"")
        .contains("name=\"containerPort\"")
        .contains("name=\"name\"")
        .contains("type=\"submit\"");
    assertThat(openingTag(form, "name=\"publish\"")).contains("checked");
  }

  @Test
  void bindingFormBindsAContainerNameAndGoesToTheServicePage() {
    String name = newContainerName();
    String web = newServiceName("web");

    ResponseEntity<String> response =
        postForm(
            "/containers/bind",
            Map.of(
                "kind",
                "CONTAINER",
                "container",
                name,
                "containerPort",
                "8080",
                "name",
                web,
                "publish",
                "on"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER);
    assertThat(response.getHeaders().getLocation()).hasPath("/services/ws-test/" + web);
    JsonNode service = get(servicePath(web)).getBody();
    assertThat(service.get("containerBindings").get(0).get("id").asString())
        .isEqualTo(name + ":8080");
    assertThat(service.get("publication").get("scope").asString()).isEqualTo("local");
  }

  @Test
  void bindingFormBindsAComposeServiceAndLeavesItUnpublishedWithoutTheCheckbox() {
    String project = newComposeProject();
    String web = newServiceName("web");

    ResponseEntity<String> response =
        postForm(
            "/containers/bind",
            Map.of(
                "kind",
                "COMPOSE",
                "composeProject",
                project,
                "composeService",
                "web",
                "containerPort",
                "80",
                "name",
                web));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER);
    JsonNode service = get(servicePath(web)).getBody();
    assertThat(service.get("containerBindings").get(0).get("id").asString())
        .isEqualTo("compose:" + project + ":web:80");
    assertThat(service.get("state").asString()).isEqualTo("unpublished");
  }

  @Test
  void bindingFormShowsTheErrorAtItsFieldAndKeepsWhatWasTyped() {
    String name = newContainerName();

    ResponseEntity<String> response =
        postForm(
            "/containers/bind",
            Map.of("kind", "CONTAINER", "container", name, "containerPort", "", "name", "web"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).contains("<h1>Bind a container</h1>");
    String form = formTo(response.getBody(), "/containers/bind");
    assertThat(openingTag(form, "name=\"containerPort\"")).contains("aria-invalid=\"true\"");
    assertThat(openingTag(response.getBody(), "name=\"container\""))
        .contains("value=\"" + name + "\"");
    assertThat(openingTag(response.getBody(), "name=\"publish\"")).doesNotContain("checked");
  }

  @Test
  void bindingFormKeepsTheComposeChoiceWhenItFails() {
    ResponseEntity<String> response =
        postForm(
            "/containers/bind",
            Map.of(
                "kind",
                "COMPOSE",
                "composeProject",
                "Shop",
                "composeService",
                "web",
                "name",
                "web"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(openingTag(response.getBody(), "value=\"COMPOSE\"")).contains("checked");
    assertThat(openingTag(response.getBody(), "name=\"composeProject\""))
        .contains("aria-invalid=\"true\"");
  }

  @Test
  void bindingFormWithoutAServiceNameBindsIntoAServiceOfTheCleanedContainerName() {
    String id = newContainerName().substring("yard-test-".length());
    String name = "Yard_Test." + id;

    ResponseEntity<String> response =
        postForm(
            "/containers/bind",
            Map.of("kind", "CONTAINER", "container", name, "containerPort", "80", "name", " "));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER);
    assertThat(response.getHeaders().getLocation()).hasPath("/services/ws-test/yard-test-" + id);
    JsonNode service = get(servicePath("yard-test-" + id)).getBody();
    assertThat(service.get("containerBindings").get(0).get("id").asString())
        .isEqualTo(name + ":80");
  }

  @Test
  void bindingFormWithoutAServiceNameBindsAComposeServiceIntoAServiceOfItsCleanedName() {
    String project = newComposeProject();
    String composeService = "Web_" + project.substring("yard-test-".length());
    String web = "web-" + project.substring("yard-test-".length());

    ResponseEntity<String> response =
        postForm(
            "/containers/bind",
            Map.of(
                "kind",
                "COMPOSE",
                "composeProject",
                project,
                "composeService",
                composeService,
                "containerPort",
                "80"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER);
    assertThat(response.getHeaders().getLocation()).hasPath("/services/ws-test/" + web);
    JsonNode service = get(servicePath(web)).getBody();
    assertThat(service.get("containerBindings").get(0).get("id").asString())
        .isEqualTo("compose:" + project + ":" + composeService + ":80");
  }

  @Test
  void bindingFormOfAFollowedContainerShowsItsServiceAtTheContainerField() {
    String name = newContainerName();
    String web = newServiceName("web");
    bind(web, name, 80);

    ResponseEntity<String> response =
        postForm(
            "/containers/bind",
            Map.of("kind", "CONTAINER", "container", name, "containerPort", "9000", "name", "api"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(openingTag(response.getBody(), "name=\"container\""))
        .contains("aria-invalid=\"true\"");
    assertThat(response.getBody()).contains("of the Service " + web);
  }

  @Test
  void startingAndStoppingContainerEmitsContainersChangedOverSseStream() throws Exception {
    try (EventStreamClient client = new EventStreamClient(port)) {
      String name = newContainerName();
      GenericContainer<?> container = start(onePort(name));

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
  void startingContainerUpdatesUnfollowedContainersTableOnPage() {
    String name = newContainerName();
    assertThat(getPage("/containers").getBody()).doesNotContain(name);

    start(onePort(name));

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> assertThat(getPage("/containers").getBody()).contains(name));
  }

  @Test
  void parallelContainerStartsEmitSingleCoalescedContainersChangedSignal() throws Exception {
    try (EventStreamClient client = new EventStreamClient(port)) {
      // Allow any events from prior tests to settle
      Thread.sleep(400);
      client.lines.clear();

      String first = newContainerName();
      String second = newContainerName();
      GenericContainer<?> c1 = onePort(first);
      GenericContainer<?> c2 = onePort(second);
      Thread t1 = Thread.ofVirtual().start(() -> start(c1));
      Thread t2 = Thread.ofVirtual().start(() -> start(c2));
      t1.join();
      t2.join();
      await()
          .atMost(Duration.ofSeconds(10))
          .until(() -> client.lines.contains("event:containers-changed"));

      Thread.sleep(600);

      long count =
          client.lines.stream().filter(line -> line.equals("event:containers-changed")).count();
      assertThat(count).isEqualTo(1);
    }
  }

  @Test
  void bindingFormPageHasNoRefreshThatCouldResetWhatIsTyped() {
    String page = getPage("/containers/new").getBody();
    assertThat(page).doesNotContain("id=\"containers\"").doesNotContain("hx-get=\"/containers\"");

    String name = newContainerName();
    ResponseEntity<String> response =
        postForm(
            "/containers/bind",
            Map.of(
                "kind", "CONTAINER",
                "container", name,
                "containerPort", "",
                "name", "my-input-preserved"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    String responseBody = response.getBody();
    assertThat(responseBody).doesNotContain("id=\"containers\"");
    assertThat(openingTag(responseBody, "name=\"container\"")).contains("value=\"" + name + "\"");
    assertThat(openingTag(responseBody, "name=\"name\"")).contains("value=\"my-input-preserved\"");
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

  private static String registerPath(String container) {
    return PATH + "/" + container + "/register";
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

  private static String rowOf(String page, String container) {
    int at = page.indexOf(">" + container + "<");
    assertThat(at).as("a row for %s", container).isNotNegative();
    return page.substring(page.lastIndexOf("<tr", at), page.indexOf("</tr>", at));
  }
}
