package dev.palazon.lvhyard.dashboard.docker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.palazon.lvhyard.dashboard.docker.Docker.Container;
import dev.palazon.lvhyard.dashboard.docker.Docker.ContainerEvents;
import dev.palazon.lvhyard.dashboard.docker.Docker.Event;
import dev.palazon.lvhyard.dashboard.docker.Docker.Port;
import dev.palazon.lvhyard.dashboard.docker.FakeDocker.Spec;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * What the dashboard needs Docker to do, run against {@link FakeDocker} and against real Docker, so
 * that the fake cannot drift from the real one.
 */
abstract class DockerContract {

  abstract Docker docker();

  /** Starts a container as {@code spec} describes it, and waits until Docker lists it. */
  abstract void start(Spec spec);

  abstract void stop(String name);

  @Test
  void runningContainerIsListedByNameWithAnAddressAndNoLabelsOrHealth() {
    Spec spec = FakeDocker.container(newName());

    start(spec);

    Container container = listed(spec.name());
    assertThat(container.name()).isEqualTo(spec.name());
    assertThat(container.address()).isPresent();
    assertThat(container.composeProject()).isEmpty();
    assertThat(container.healthState()).isEmpty();
  }

  @Test
  void composeLabelsNameTheProjectAndTheService() {
    Spec spec = FakeDocker.container(newName()).compose("shop", "web");

    start(spec);

    Container container = listed(spec.name());
    assertThat(container.composeProject()).contains("shop");
    assertThat(container.composeService()).contains("web");
    assertThat(container.isOfComposeService("shop", "web")).isTrue();
  }

  @Test
  void unpublishedPortHasNoHostPortAndAPublishedOneHasOnePerHostAddress() {
    Spec spec = FakeDocker.container(newName()).port(9000).published(80);

    start(spec);

    List<Port> ports = listed(spec.name()).ports();
    assertThat(ports)
        .filteredOn(port -> port.privatePort() == 9000)
        .singleElement()
        .satisfies(port -> {
          assertThat(port.publicPort()).isNull();
          assertThat(port.ip()).isEmpty();
        });
    List<Port> published = listed(spec.name()).published(80);
    assertThat(published).isNotEmpty();
    assertThat(published).allSatisfy(port -> assertThat(port.publicPort()).isPositive());
    assertThat(published).extracting(Port::ip).isSubsetOf("0.0.0.0", "::");
  }

  @Test
  void portPublishedOnLoopbackNamesThatAddress() {
    Spec spec = FakeDocker.container(newName()).publishedOn("127.0.0.1", 80);

    start(spec);

    assertThat(listed(spec.name()).published(80))
        .singleElement()
        .satisfies(port -> assertThat(port.ip()).isEqualTo("127.0.0.1"));
  }

  @Test
  void containerOnTheHostsNetworkHasNoAddress() {
    Spec spec = FakeDocker.container(newName()).hostNetwork();

    start(spec);

    assertThat(listed(spec.name()).address()).isEmpty();
  }

  @Test
  void healthCheckGivesItsState() {
    Spec spec = FakeDocker.container(newName()).health("healthy");

    start(spec);

    await()
        .atMost(Duration.ofSeconds(15))
        .until(() -> listed(spec.name()).healthState().orElse(""), "healthy"::equals);
  }

  @Test
  void stoppedContainerLeavesTheListAndItsStartAndDieAreSent() throws Exception {
    Instant before = Instant.now().minusSeconds(2);
    Spec spec = FakeDocker.container(newName());
    start(spec);

    stop(spec.name());

    assertThat(docker().runningContainers()).noneMatch(container -> container.isNamed(spec.name()));
    try (ContainerEvents events = docker().containerEvents(before)) {
      List<String> actions = new ArrayList<>();
      CompletableFuture.runAsync(
              () -> {
                while (!actions.contains("die") && events.hasNext()) {
                  Event event = events.next();
                  assertThat(event.type()).isEqualTo("container");
                  actions.add(event.action());
                }
              })
          .get(20, TimeUnit.SECONDS);
      assertThat(actions).contains("start", "die");
    }
  }

  @Test
  void everyContainerListsAStoppedOneAsNotRunningWithItsLabels() {
    Spec spec = FakeDocker.container(newName()).compose("shop", "web");
    start(spec);
    assertThat(every(spec.name()).running()).isTrue();

    stop(spec.name());

    Container stopped = every(spec.name());
    assertThat(stopped.running()).isFalse();
    assertThat(stopped.composeService()).contains("web");
  }

  @Test
  void versionAndPingAnswer() {
    docker().ping();
    assertThat(docker().version().apiVersion()).isNotBlank();
  }

  private Container listed(String name) {
    return docker().runningContainers().stream()
        .filter(container -> container.isNamed(name))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Docker does not list " + name));
  }

  private Container every(String name) {
    return docker().containers().stream()
        .filter(container -> container.isNamed(name))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Docker does not list " + name + " at all"));
  }

  private static String newName() {
    return "contract-" + UUID.randomUUID().toString().substring(0, 8);
  }
}
