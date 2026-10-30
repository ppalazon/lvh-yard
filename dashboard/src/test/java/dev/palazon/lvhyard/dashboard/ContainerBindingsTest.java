package dev.palazon.lvhyard.dashboard;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.GenericContainer;

import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** Container Bindings, which give a Service a Bound Instance for each running container they match. */
class ContainerBindingsTest extends DashboardTest {

    /** Long enough for Docker's event to reach the dashboard, and for it to register or remove the Instance. */
    private static final Duration FOLLOW_TIMEOUT = Duration.ofSeconds(15);

    @Test
    void runningContainerWithAnUnpublishedPortGivesABoundInstanceAtItsOwnAddress() {
        String web = newServiceName("web");
        String name = newContainerName();
        GenericContainer<?> container = startContainer(name, 80, false);

        ResponseEntity<JsonNode> response = post(containerBindingsPath(web), Map.of("container", name, "port", 80));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        await().atMost(FOLLOW_TIMEOUT).untilAsserted(() -> {
            JsonNode instances = get(servicePath(web)).getBody().get("instances");
            assertThat(instances).hasSize(1);
            assertThat(instances.get(0).get("host").asString()).isEqualTo(containerAddress(container));
            assertThat(instances.get(0).get("port").asInt()).isEqualTo(80);
        });
    }

    @Test
    void publishedContainerPortGivesABoundInstanceAtTheLanAddressAndTheHostPort() {
        String web = newServiceName("web");
        String name = newContainerName();
        GenericContainer<?> container = startContainer(name, 80, true);

        bind(web, name, 80);

        await().atMost(FOLLOW_TIMEOUT).untilAsserted(() -> {
            JsonNode instance = get(servicePath(web)).getBody().get("instances").get(0);
            assertThat(instance.get("host").asString()).isEqualTo(lanAddress());
            assertThat(instance.get("port").asInt()).isEqualTo(container.getMappedPort(80));
        });
    }

    @Test
    void containerThatStartsLaterGivesItsBoundInstanceByItself() {
        String web = newServiceName("web");
        String name = newContainerName();
        bind(web, name, 8080);
        assertThat(get(servicePath(web)).getBody().get("instances")).isEmpty();

        GenericContainer<?> container = startContainer(name, 8080, false);

        await().atMost(FOLLOW_TIMEOUT).untilAsserted(() -> assertThat(instanceAddresses(web))
                .containsExactly(containerAddress(container) + ":8080"));
    }

    @Test
    void stoppedContainerLosesItsBoundInstanceAndTheBindingStays() {
        String web = newServiceName("web");
        String name = newContainerName();
        GenericContainer<?> container = startContainer(name, 80, false);
        bind(web, name, 80);
        await().atMost(FOLLOW_TIMEOUT).until(() -> instanceAddresses(web).size() == 1);

        stopContainer(container);

        await().atMost(FOLLOW_TIMEOUT).untilAsserted(() -> assertThat(instanceAddresses(web)).isEmpty());
        JsonNode binding = get(servicePath(web)).getBody().get("containerBindings").get(0);
        assertThat(binding.get("container").asString()).isEqualTo(name);
        assertThat(binding.get("instances")).isEmpty();
    }

    @Test
    void removedContainerLosesItsBoundInstance() {
        String web = newServiceName("web");
        String name = newContainerName();
        GenericContainer<?> container = startContainer(name, 80, false);
        bind(web, name, 80);
        await().atMost(FOLLOW_TIMEOUT).until(() -> instanceAddresses(web).size() == 1);

        container.stop();

        await().atMost(FOLLOW_TIMEOUT).untilAsserted(() -> assertThat(instanceAddresses(web)).isEmpty());
    }

    @Test
    void boundInstanceGetsTheDefaultTcpCheck() {
        String web = newServiceName("web");
        String name = newContainerName();
        GenericContainer<?> container = startContainer(name, 80, false);

        bind(web, name, 80);

        await().atMost(FOLLOW_TIMEOUT).untilAsserted(() -> {
            JsonNode instance = get(servicePath(web)).getBody().get("instances").get(0);
            assertThat(instance.get("check").get("type").asString()).isEqualTo("tcp");
            assertThat(instance.get("check").get("interval").asString()).isEqualTo("10s");
            assertThat(instance.get("check").get("timeout").asString()).isEqualTo("2s");
            assertThat(instance.get("checks").valueStream().map(check -> check.get("name").asString()))
                    .contains("TCP " + containerAddress(container) + ":80");
        });
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
        await().atMost(FOLLOW_TIMEOUT).untilAsserted(() -> {
            JsonNode service = get(servicePath(web)).getBody();
            String instanceId = service.get("instances").get(0).get("id").asString();
            assertThat(service.get("instances").get(0).get("containerBinding").asString())
                    .isEqualTo(running + ":80");
            assertThat(service.get("containerBindings").valueStream()
                    .map(binding -> binding.get("container").asString() + " " + binding.get("port").asInt() + " "
                            + binding.get("instances")))
                    .containsExactlyInAnyOrder(running + " 80 [\"" + instanceId + "\"]", missing + " 9000 []");
        });
    }

    @Test
    void serviceWithOnlyABindingThatMatchesNoContainerIsInTheCatalog() {
        String web = newServiceName("web");

        bind(web, newContainerName(), 80);

        JsonNode service = StaticInstancesTest.findService(get("/api/services").getBody(), web);
        assertThat(service.get("state").asString()).isEqualTo("unpublished");
        assertThat(service.get("instances")).isEmpty();
        assertThat(service.get("containerBindings")).hasSize(1);
    }

    @Test
    void bindingTheSameContainerAndPortAgainChangesNothing() {
        String web = newServiceName("web");
        String name = newContainerName();

        bind(web, name, 80);
        bind(web, name, 80);

        assertThat(get(servicePath(web)).getBody().get("containerBindings")).hasSize(1);
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
    void containerNameDockerWouldRefuseIsRefused() {
        String web = newServiceName("web");

        ResponseEntity<JsonNode> response = post(containerBindingsPath(web), Map.of("container", "my web", "port", 80));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("detail").asString()).contains("my web").contains("Docker");
    }

    @Test
    void containerPortIsRequired() {
        String web = newServiceName("web");

        ResponseEntity<JsonNode> response = post(containerBindingsPath(web), Map.of("container", "web"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("detail").asString()).contains("port");
    }

    @Test
    void serviceNameIsCheckedAsForAStaticInstance() {
        ResponseEntity<JsonNode> response = post(containerBindingsPath("yard"), Map.of("container", "web", "port", 80));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("detail").asString()).contains("dashboard");
    }

    @Test
    void remoteServicesBindingsAreShownAndCannotBeChangedHere() {
        String web = newServiceName("web");
        remote.bind(web, newContainerName(), 80);

        JsonNode service = get("/api/services/" + RemoteWorkstation.NAME + "/" + web).getBody();
        ResponseEntity<JsonNode> change = post("/api/services/" + RemoteWorkstation.NAME + "/" + web
                + "/container-bindings", Map.of("container", "web", "port", 80));

        assertThat(service.get("containerBindings").get(0).get("port").asInt()).isEqualTo(80);
        assertThat(change.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
    }

    @Test
    void dashboardCatchesUpOnStartupWithContainersThatChangedWhileItWasNotRunning() {
        String started = newServiceName("started");
        String stopped = newServiceName("stopped");
        String startedName = newContainerName();
        String stoppedName = newContainerName();
        GenericContainer<?> stopping = startContainer(stoppedName, 80, false);
        remote.bind(started, startedName, 80);
        remote.bind(stopped, stoppedName, 80);
        await().atMost(FOLLOW_TIMEOUT).until(() -> remoteInstanceAddresses(stopped).size() == 1);

        GenericContainer<?>[] starting = new GenericContainer<?>[1];
        remote.whileDashboardIsStopped(() -> {
            stopping.stop();
            starting[0] = startContainer(startedName, 80, false);
        });

        await().atMost(FOLLOW_TIMEOUT).untilAsserted(() -> {
            assertThat(remoteInstanceAddresses(started)).containsExactly(containerAddress(starting[0]) + ":80");
            assertThat(remoteInstanceAddresses(stopped)).isEmpty();
        });
    }

    @Test
    void composeBindingGivesABoundInstanceForEachContainerOfThatProjectAndService() {
        String web = newServiceName("web");
        String project = newComposeProject();
        GenericContainer<?> first = startComposeContainer(newContainerName(), project, "web", 80);
        GenericContainer<?> second = startComposeContainer(newContainerName(), project, "web", 80);
        startComposeContainer(newContainerName(), project, "db", 80);
        startComposeContainer(newContainerName(), newComposeProject(), "web", 80);

        JsonNode added = bindCompose(web, project, "web", 80);

        assertThat(added.get("composeProject").asString()).isEqualTo(project);
        assertThat(added.get("composeService").asString()).isEqualTo("web");
        assertThat(added.has("container")).isFalse();
        await().atMost(FOLLOW_TIMEOUT).untilAsserted(() -> assertThat(instanceAddresses(web))
                .containsExactlyInAnyOrder(containerAddress(first) + ":80", containerAddress(second) + ":80"));
        JsonNode binding = get(servicePath(web)).getBody().get("containerBindings").get(0);
        assertThat(binding.get("instances")).hasSize(2);
    }

    @Test
    void scalingTheComposeServiceUpAndDownAddsAndRemovesBoundInstances() {
        String web = newServiceName("web");
        String project = newComposeProject();
        GenericContainer<?> first = startComposeContainer(newContainerName(), project, "web", 80);
        bindCompose(web, project, "web", 80);
        await().atMost(FOLLOW_TIMEOUT).until(() -> instanceAddresses(web).size() == 1);

        GenericContainer<?> second = startComposeContainer(newContainerName(), project, "web", 80);
        await().atMost(FOLLOW_TIMEOUT).untilAsserted(() -> assertThat(instanceAddresses(web))
                .containsExactlyInAnyOrder(containerAddress(first) + ":80", containerAddress(second) + ":80"));

        stopContainer(first);
        await().atMost(FOLLOW_TIMEOUT).untilAsserted(() -> assertThat(instanceAddresses(web))
                .containsExactly(containerAddress(second) + ":80"));
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
    void containerBoundByNameAndByItsComposeServiceKeepsTheOtherBoundInstanceWhenOneBindingIsRemoved() {
        String web = newServiceName("web");
        String project = newComposeProject();
        String name = newContainerName();
        GenericContainer<?> container = startComposeContainer(name, project, "web", 80);
        String byName = bind(web, name, 80).get("id").asString();
        bindCompose(web, project, "web", 80);
        await().atMost(FOLLOW_TIMEOUT).untilAsserted(() -> assertThat(get(servicePath(web)).getBody()
                .get("containerBindings").valueStream().map(binding -> binding.get("instances").size()))
                .containsExactly(1, 1));

        delete(containerBindingsPath(web) + "/" + byName);

        assertThat(instanceAddresses(web)).containsExactly(containerAddress(container) + ":80");
    }

    @Test
    void bindingNeedsAContainerOrAComposeService() {
        ResponseEntity<JsonNode> response = post(containerBindingsPath(newServiceName("web")), Map.of("port", 80));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("detail").asString()).contains("container name")
                .contains("Compose project and service");
    }

    @Test
    void bindingToAContainerAndAComposeServiceAtOnceIsRefused() {
        ResponseEntity<JsonNode> response = post(containerBindingsPath(newServiceName("web")),
                Map.of("container", "web", "composeProject", "shop", "composeService", "web", "port", 80));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("detail").asString()).contains("not both");
    }

    @Test
    void composeServiceNeedsItsProject() {
        ResponseEntity<JsonNode> response = post(containerBindingsPath(newServiceName("web")),
                Map.of("composeService", "web", "port", 80));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("detail").asString()).contains("project");
    }

    @Test
    void composeProjectNameComposeWouldRefuseIsRefused() {
        ResponseEntity<JsonNode> response = post(containerBindingsPath(newServiceName("web")),
                Map.of("composeProject", "My Shop", "composeService", "web", "port", 80));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("detail").asString()).contains("My Shop").contains("Compose");
    }

    /** Where each Instance of this Workstation's {@code service} is, as {@code host:port}. */
    private List<String> instanceAddresses(String service) {
        return addresses(get(servicePath(service)).getBody());
    }

    /** Where each Instance of the remote Workstation's {@code service} is, as this Workstation reads it. */
    private List<String> remoteInstanceAddresses(String service) {
        return addresses(get("/api/services/" + RemoteWorkstation.NAME + "/" + service).getBody());
    }

    private static List<String> addresses(JsonNode service) {
        return service.get("instances").valueStream()
                .map(instance -> instance.get("host").asString() + ":" + instance.get("port").asInt()).toList();
    }
}
