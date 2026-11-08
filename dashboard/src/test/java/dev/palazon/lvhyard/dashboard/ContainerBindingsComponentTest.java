package dev.palazon.lvhyard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.palazon.lvhyard.dashboard.docker.Docker.Container;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/**
 * Container Bindings, which give a Service a Bound Instance for each running container they match.
 */
class ContainerBindingsComponentTest extends DashboardComponentTest {

  /**
   * Long enough for Docker's event to reach the dashboard, and for it to register or remove the
   * Instance.
   */
  private static final Duration FOLLOW_TIMEOUT = Duration.ofSeconds(15);

  /** Where each Instance of this Workstation's {@code service} is, as {@code host:port}. */
  private List<String> instanceAddresses(String service) {
    return addresses(get(servicePath(service)).getBody());
  }

  private static List<String> addresses(JsonNode service) {
    return service
        .get("instances")
        .valueStream()
        .map(instance -> instance.get("host").asString() + ":" + instance.get("port").asInt())
        .toList();
  }

  @Test
  void runningContainerWithAnUnpublishedPortGivesABoundInstanceAtItsOwnAddress() {
    String web = newServiceName("web");
    String name = newContainerName();
    Container container = startContainer(name, 80, false);

    ResponseEntity<JsonNode> response =
        post(containerBindingsPath(web), Map.of("container", name, "port", 80));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    await()
        .atMost(FOLLOW_TIMEOUT)
        .untilAsserted(() -> {
          JsonNode instances = get(servicePath(web)).getBody().get("instances");
          assertThat(instances).hasSize(1);
          assertThat(instances.get(0).get("host").asString())
              .isEqualTo(containerAddress(container));
          assertThat(instances.get(0).get("port").asInt()).isEqualTo(80);
          assertThat(instances.get(0).get("container").asString()).isEqualTo(name);
          assertThat(instances.get(0).get("containerPort").asInt()).isEqualTo(80);
          assertThat(instances.get(0).has("hostPort")).isFalse();
          assertThat(instances.get(0).get("meta")).isEmpty();
        });
  }

  @Test
  void publishedContainerPortGivesABoundInstanceAtTheLanAddressAndTheHostPort() {
    String web = newServiceName("web");
    String name = newContainerName();
    Container container = startContainer(name, 80, true);

    bind(web, name, 80);

    await()
        .atMost(FOLLOW_TIMEOUT)
        .untilAsserted(() -> {
          JsonNode instance = get(servicePath(web)).getBody().get("instances").get(0);
          assertThat(instance.get("host").asString()).isEqualTo(lanAddress());
          assertThat(instance.get("port").asInt()).isEqualTo(hostPort(container, 80));
          assertThat(instance.get("container").asString()).isEqualTo(name);
          assertThat(instance.get("containerPort").asInt()).isEqualTo(80);
          assertThat(instance.get("hostPort").asInt()).isEqualTo(hostPort(container, 80));
        });
  }

  @Test
  void containerThatStartsLaterGivesItsBoundInstanceByItself() {
    String web = newServiceName("web");
    String name = newContainerName();
    bind(web, name, 8080);
    assertThat(get(servicePath(web)).getBody().get("instances")).isEmpty();

    Container container = startContainer(name, 8080, false);

    await()
        .atMost(FOLLOW_TIMEOUT)
        .untilAsserted(() ->
            assertThat(instanceAddresses(web))
                .containsExactly(containerAddress(container) + ":8080"));
  }

  @Test
  void stoppedContainerLosesItsBoundInstanceAndTheBindingStays() {
    String web = newServiceName("web");
    String name = newContainerName();
    Container container = startContainer(name, 80, false);
    bind(web, name, 80);
    await().atMost(FOLLOW_TIMEOUT).until(() -> instanceAddresses(web).size() == 1);

    stopContainer(container);

    await()
        .atMost(FOLLOW_TIMEOUT)
        .untilAsserted(() -> assertThat(instanceAddresses(web)).isEmpty());
    JsonNode binding = get(servicePath(web)).getBody().get("containerBindings").get(0);
    assertThat(binding.get("container").asString()).isEqualTo(name);
    assertThat(binding.get("instances")).isEmpty();
  }

  @Test
  void removedContainerLosesItsBoundInstance() {
    String web = newServiceName("web");
    String name = newContainerName();
    Container container = startContainer(name, 80, false);
    bind(web, name, 80);
    await().atMost(FOLLOW_TIMEOUT).until(() -> instanceAddresses(web).size() == 1);

    docker.remove(name);

    await()
        .atMost(FOLLOW_TIMEOUT)
        .untilAsserted(() -> assertThat(instanceAddresses(web)).isEmpty());
  }

  @Test
  void boundInstanceGetsTheDefaultTcpCheck() {
    String web = newServiceName("web");
    String name = newContainerName();
    Container container = startContainer(name, 80, false);

    bind(web, name, 80);

    await()
        .atMost(FOLLOW_TIMEOUT)
        .untilAsserted(() -> {
          JsonNode instance = get(servicePath(web)).getBody().get("instances").get(0);
          assertThat(instance.get("check").get("type").asString()).isEqualTo("tcp");
          assertThat(instance.get("check").get("interval").asString()).isEqualTo("1s");
          assertThat(instance.get("check").get("timeout").asString()).isEqualTo("2s");
          assertThat(
                  instance.get("checks").valueStream().map(check -> check.get("name").asString()))
              .contains("TCP " + containerAddress(container) + ":80");
        });
  }

  @Test
  void yardScopedServiceWarnsAboutABoundInstanceOnAnUnpublishedContainerPortUntilItIsLocal() {
    String web = newServiceName("web");
    String name = newContainerName();
    Container container = startContainer(name, 80, false);
    bind(web, name, 80);
    await().atMost(FOLLOW_TIMEOUT).until(() -> instanceAddresses(web).size() == 1);

    JsonNode warnings =
        put(publicationPath(web), Map.of("scope", "yard")).getBody().get("warnings");

    assertThat(warnings.valueStream().map(warning -> warning.get("kind").asString()))
        .containsExactly("unpublished-container-port");
    assertThat(warnings.get(0).get("severity").asString()).isEqualTo("risk");
    assertThat(warnings.get(0).get("message").asString())
        .contains(containerAddress(container) + ":80")
        .contains("not published");
    assertThat(put(publicationPath(web), Map.of("scope", "local")).getBody().get("warnings"))
        .isEmpty();
  }

  @Test
  void warningAboutAnUnpublishedContainerPortGoesAwayWhenTheContainerIsRestartedWithItPublished() {
    String web = newServiceName("web");
    String name = newContainerName();
    Container unpublished = startContainer(name, 80, false);
    bind(web, name, 80);
    await().atMost(FOLLOW_TIMEOUT).until(() -> instanceAddresses(web).size() == 1);
    assertThat(put(publicationPath(web), Map.of("scope", "yard")).getBody().get("warnings"))
        .hasSize(1);

    docker.remove(name);
    Container published = startContainer(name, 80, true);

    await()
        .atMost(FOLLOW_TIMEOUT)
        .untilAsserted(() -> {
          JsonNode service = get(servicePath(web)).getBody();
          assertThat(addresses(service))
              .containsExactly(lanAddress() + ":" + hostPort(published, 80));
          assertThat(service.get("warnings")).isEmpty();
        });
  }

  @Test
  void containerOnTheHostsNetworkGivesABoundInstanceAtTheLanAddressWithNoWarning()
      throws IOException {
    String web = newServiceName("web");
    String name = newContainerName();
    int port = startHostNetworkContainer(name);
    bind(web, name, port);
    await().atMost(FOLLOW_TIMEOUT).until(() -> instanceAddresses(web).size() == 1);

    JsonNode service = put(publicationPath(web), Map.of("scope", "yard")).getBody();

    assertThat(addresses(service)).containsExactly(lanAddress() + ":" + port);
    assertThat(service.get("warnings")).isEmpty();
    JsonNode instance = service.get("instances").get(0);
    assertThat(instance.get("container").asString()).isEqualTo(name);
    assertThat(instance.get("containerPort").asInt()).isEqualTo(port);
    assertThat(instance.get("hostPort").asInt()).isEqualTo(port);
  }

  @Test
  void localScopedServiceDoesNotWarnAboutAnUnpublishedContainerPort() {
    String web = newServiceName("web");
    String name = newContainerName();
    startContainer(name, 80, false);
    bind(web, name, 80);
    await().atMost(FOLLOW_TIMEOUT).until(() -> instanceAddresses(web).size() == 1);

    JsonNode service = put(publicationPath(web), Map.of("scope", "local")).getBody();

    assertThat(service.get("warnings")).isEmpty();
  }

  @Test
  void apiShowsEachBindingWithTheBoundInstancesItGives() {
    String web = newServiceName("web");
    String running = newContainerName();
    String missing = newContainerName();
    startContainer(running, 80, false);

    JsonNode added = bind(web, running, 80);
    bind(web, missing, 9000);

    assertThat(added.get("id").asString()).isEqualTo(running + ":80");
    await()
        .atMost(FOLLOW_TIMEOUT)
        .untilAsserted(() -> {
          JsonNode service = get(servicePath(web)).getBody();
          String instanceId = service.get("instances").get(0).get("id").asString();
          assertThat(service.get("instances").get(0).get("containerBinding").asString())
              .isEqualTo(running + ":80");
          assertThat(
                  service
                      .get("containerBindings")
                      .valueStream()
                      .map(binding ->
                          binding.get("container").asString()
                              + " "
                              + binding.get("port").asInt()
                              + " "
                              + binding.get("instances")))
              .containsExactlyInAnyOrder(
                  running + " 80 [\"" + instanceId + "\"]", missing + " 9000 []");
        });
  }

  @Test
  void serviceWithOnlyABindingThatMatchesNoContainerIsInTheCatalog() {
    String web = newServiceName("web");

    bind(web, newContainerName(), 80);

    JsonNode service =
        StaticInstancesComponentTest.findService(get("/api/services").getBody(), web);
    assertThat(service.get("state").asString()).isEqualTo("unpublished");
    assertThat(service.get("instances")).isEmpty();
    assertThat(service.get("containerBindings")).hasSize(1);
  }

  @Test
  void bindingANewServicePublishesItWithLocalScope() {
    String web = newServiceName("web");

    ResponseEntity<JsonNode> response =
        post(containerBindingsPath(web), Map.of("container", newContainerName(), "port", 80));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    JsonNode service = get(servicePath(web)).getBody();
    assertThat(service.get("publication").get("scope").asString()).isEqualTo("local");
  }

  @Test
  void bindingANewServiceWithPublishFalseLeavesItUnpublished() {
    String web = newServiceName("web");

    post(
        containerBindingsPath(web),
        Map.of("container", newContainerName(), "port", 80, "publish", false));

    assertThat(get(servicePath(web)).getBody().get("state").asString()).isEqualTo("unpublished");
  }

  @Test
  void bindingIntoAnUnpublishedServiceKeepsItUnpublished() {
    String web = newServiceName("web");
    bind(web, newContainerName(), 80);

    post(
        containerBindingsPath(web),
        Map.of("container", newContainerName(), "port", 80, "publish", true));

    assertThat(get(servicePath(web)).getBody().get("state").asString()).isEqualTo("unpublished");
  }

  @Test
  void bindingAContainerNameThatABindingFollowsIsRefusedWithItsServiceWhateverThePort() {
    String web = newServiceName("web");
    String api = newServiceName("api");
    String name = newContainerName();
    bind(web, name, 80);

    ResponseEntity<JsonNode> response =
        post(containerBindingsPath(api), Map.of("container", name, "port", 9000));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody().get("detail").asString()).contains(name).contains(web);
    assertThat(get(servicePath(api)).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void bindingTheSameContainerAgainIsRefused() {
    String web = newServiceName("web");
    String name = newContainerName();
    bind(web, name, 80);

    ResponseEntity<JsonNode> response =
        post(containerBindingsPath(web), Map.of("container", name, "port", 80));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(get(servicePath(web)).getBody().get("containerBindings")).hasSize(1);
  }

  @Test
  void bindingAComposeServiceThatABindingFollowsIsRefusedWithItsService() {
    String web = newServiceName("web");
    String api = newServiceName("api");
    String project = newComposeProject();
    bindCompose(web, project, "web", 80);

    ResponseEntity<JsonNode> response =
        post(
            containerBindingsPath(api),
            Map.of("composeProject", project, "composeService", "web", "port", 8080));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody().get("detail").asString())
        .contains("Compose service web of project " + project)
        .contains(web);
  }

  @Test
  void removingABindingRemovesItsBoundInstancesAndAServiceWithNothingLeft() {
    String web = newServiceName("web");
    String name = newContainerName();
    startContainer(name, 80, false);
    String id = bind(web, name, 80).get("id").asString();
    await().atMost(FOLLOW_TIMEOUT).until(() -> instanceAddresses(web).size() == 1);

    ResponseEntity<JsonNode> response = delete(containerBindingsPath(web) + "/" + id);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(get(servicePath(web)).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void removingAnUnknownBindingIsNotFound() {
    String web = newServiceName("web");
    bind(web, newContainerName(), 80);

    ResponseEntity<JsonNode> response = delete(containerBindingsPath(web) + "/nothing:80");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody().get("detail").asString()).contains("nothing:80");
  }

  @Test
  void containerPortIsRequired() {
    String web = newServiceName("web");

    ResponseEntity<JsonNode> response =
        post(containerBindingsPath(web), Map.of("container", "web"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().get("detail").asString()).contains("port");
  }

  @Test
  void serviceNameIsCheckedAsForAStaticInstance() {
    ResponseEntity<JsonNode> response =
        post(containerBindingsPath("consul"), Map.of("container", "web", "port", 80));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().get("detail").asString()).contains("Consul's own entry");
  }

  @Test
  void composeBindingGivesABoundInstanceForEachContainerOfThatProjectAndService() {
    String web = newServiceName("web");
    String project = newComposeProject();
    String firstName = newContainerName();
    String secondName = newContainerName();
    Container first = startComposeContainer(firstName, project, "web", 80);
    Container second = startComposeContainer(secondName, project, "web", 80);
    startComposeContainer(newContainerName(), project, "db", 80);
    startComposeContainer(newContainerName(), newComposeProject(), "web", 80);

    JsonNode added = bindCompose(web, project, "web", 80);

    assertThat(added.get("composeProject").asString()).isEqualTo(project);
    assertThat(added.get("composeService").asString()).isEqualTo("web");
    assertThat(added.has("container")).isFalse();
    await()
        .atMost(FOLLOW_TIMEOUT)
        .untilAsserted(() ->
            assertThat(instanceAddresses(web))
                .containsExactlyInAnyOrder(
                    containerAddress(first) + ":80", containerAddress(second) + ":80"));
    JsonNode binding = get(servicePath(web)).getBody().get("containerBindings").get(0);
    assertThat(binding.get("instances")).hasSize(2);
    JsonNode instances = get(servicePath(web)).getBody().get("instances");
    assertThat(instances.valueStream().map(instance -> instance.get("container").asString()))
        .containsExactlyInAnyOrder(firstName, secondName);
    for (JsonNode instance : instances) {
      assertThat(instance.get("containerPort").asInt()).isEqualTo(80);
      assertThat(instance.has("hostPort")).isFalse();
    }
  }

  @Test
  void scalingTheComposeServiceUpAndDownAddsAndRemovesBoundInstances() {
    String web = newServiceName("web");
    String project = newComposeProject();
    Container first = startComposeContainer(newContainerName(), project, "web", 80);
    bindCompose(web, project, "web", 80);
    await().atMost(FOLLOW_TIMEOUT).until(() -> instanceAddresses(web).size() == 1);

    Container second = startComposeContainer(newContainerName(), project, "web", 80);
    await()
        .atMost(FOLLOW_TIMEOUT)
        .untilAsserted(() ->
            assertThat(instanceAddresses(web))
                .containsExactlyInAnyOrder(
                    containerAddress(first) + ":80", containerAddress(second) + ":80"));

    stopContainer(first);
    await()
        .atMost(FOLLOW_TIMEOUT)
        .untilAsserted(() ->
            assertThat(instanceAddresses(web)).containsExactly(containerAddress(second) + ":80"));
  }

  @Test
  void removingAComposeBindingRemovesEveryBoundInstanceItGives() {
    String web = newServiceName("web");
    String project = newComposeProject();
    startComposeContainer(newContainerName(), project, "web", 80);
    startComposeContainer(newContainerName(), project, "web", 80);
    String id = bindCompose(web, project, "web", 80).get("id").asString();
    await().atMost(FOLLOW_TIMEOUT).until(() -> instanceAddresses(web).size() == 2);

    ResponseEntity<JsonNode> response = delete(containerBindingsPath(web) + "/" + id);

    assertThat(id).isEqualTo("compose:" + project + ":web:80");
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(get(servicePath(web)).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void
      containerBoundByNameAndByItsComposeServiceKeepsTheOtherBoundInstanceWhenOneBindingIsRemoved() {
    String web = newServiceName("web");
    String project = newComposeProject();
    String name = newContainerName();
    Container container = startComposeContainer(name, project, "web", 80);
    String byName = bind(web, name, 80).get("id").asString();
    bindCompose(web, project, "web", 80);
    await()
        .atMost(FOLLOW_TIMEOUT)
        .untilAsserted(() ->
            assertThat(
                    get(servicePath(web))
                        .getBody()
                        .get("containerBindings")
                        .valueStream()
                        .map(binding -> binding.get("instances").size()))
                .containsExactly(1, 1));

    delete(containerBindingsPath(web) + "/" + byName);

    assertThat(instanceAddresses(web)).containsExactly(containerAddress(container) + ":80");
  }
}
