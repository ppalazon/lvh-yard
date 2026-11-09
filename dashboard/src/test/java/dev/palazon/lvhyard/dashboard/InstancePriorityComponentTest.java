package dev.palazon.lvhyard.dashboard;

import static dev.palazon.lvhyard.dashboard.TraefikConfigurationComponentTest.serverUrlsAt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.palazon.lvhyard.dashboard.docker.Docker.Container;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * The Instance Priority wired end to end: real checks, Aliases, other Workstations and Traefik.
 * RoutingTest covers each case of the Route choice itself.
 */
class InstancePriorityComponentTest extends DashboardComponentTest {

  /** Long enough for a Bound Instance to appear and for its first check to run. */
  static final Duration SWITCH_TIMEOUT = Duration.ofSeconds(30);

  /**
   * Registers a Static Instance of {@code service} that listens on the LAN address, with a check
   * every second, and answers its address.
   */
  String staticInstance(String service) {
    int port = newLanPort();
    listenOnLanAddress(port);
    register(service, Map.of("port", port, "check", Map.of("interval", "1s")));
    return lanAddress() + ":" + port;
  }

  /**
   * Binds {@code service} to a new container that listens, waits until its Bound Instance passes,
   * and answers its address. So a test never sees the Route before the Bound Instance is there.
   */
  String boundInstance(String service) {
    String name = newContainerName();
    Container container = startContainer(name, 80, false);
    bind(service, name, 80);
    await()
        .atMost(SWITCH_TIMEOUT)
        .until(() ->
            get(servicePath(service))
                .getBody()
                .get("instances")
                .valueStream()
                .anyMatch(instance ->
                    instance.has("containerBinding")
                        && instance.get("health").asString().equals("passing")));
    return containerAddress(container) + ":80";
  }

  /** Waits until this Workstation routes {@code service} to exactly {@code addresses}. */
  void awaitRoutedTo(String service, String... addresses) {
    awaitRoutedTo(
        () -> get("/traefik/configuration").getBody().get("http"),
        service + ".lvh.test",
        addresses);
  }

  /** Waits until {@code configuration} routes {@code hostname} to exactly {@code addresses}. */
  static void awaitRoutedTo(
      Supplier<JsonNode> configuration, String hostname, String... addresses) {
    await()
        .atMost(SWITCH_TIMEOUT)
        .untilAsserted(() ->
            assertThat(serverUrlsAt(configuration.get(), hostname))
                .containsExactlyInAnyOrder(
                    Arrays.stream(addresses)
                        .map(address -> "http://" + address)
                        .toArray(String[]::new)));
  }

  /** The port of an address that {@link #staticInstance} answers. */
  static int port(String address) {
    return Integer.parseInt(address.substring(address.lastIndexOf(':') + 1));
  }

  static List<String> texts(JsonNode array) {
    return array.valueStream().map(JsonNode::asString).toList();
  }

  @Test
  void routeMovesToTheBoundInstanceWhenTheStaticInstanceStopsAndBackWhenItStartsAgain() {
    String web = newServiceName("web");
    int port = newLanPort();
    listenOnLanAddress(port);
    register(web, Map.of("port", port, "check", Map.of("interval", "1s")));
    String bound = boundInstance(web);
    publish(web);
    String terminal = lanAddress() + ":" + port;
    awaitRoutedTo(web, terminal);

    stopListeningOnLanAddress(port);

    awaitRoutedTo(web, bound);

    listenOnLanAddress(port);

    awaitRoutedTo(web, terminal);
  }

  @Test
  void apiAndServicePageShowTheKindInUseAndTheKindsWaiting() {
    String web = newServiceName("web");
    staticInstance(web);
    boundInstance(web);

    publish(web);

    await()
        .atMost(SWITCH_TIMEOUT)
        .untilAsserted(() -> {
          JsonNode service = get(servicePath(web)).getBody();
          String terminal =
              service
                  .get("instances")
                  .valueStream()
                  .filter(instance -> !instance.has("containerBinding"))
                  .findFirst()
                  .orElseThrow()
                  .get("id")
                  .asString();
          assertThat(texts(service.get("priority"))).containsExactly("static", "bound", "alias");
          assertThat(service.get("routing").path("kind").asString()).isEqualTo("static");
          assertThat(texts(service.get("routing").get("instances"))).containsExactly(terminal);
          assertThat(summaryCard(getPage("/services/ws-test/" + web).getBody(), "routing"))
              .contains("Static")
              .contains("Bound waiting");
        });
  }

  @Test
  void unpublishedServiceHasNoInstancePriorityOrRouting() {
    String web = newServiceName("web");
    staticInstance(web);

    JsonNode service = get(servicePath(web)).getBody();

    assertThat(service.has("priority")).isFalse();
    assertThat(service.has("routing")).isFalse();
  }

  @Test
  void publicationWrittenBeforeInstancePriorityRoutesWithTheDefaultOrder() throws Exception {
    String web = newServiceName("web");
    String terminal = staticInstance(web);
    boundInstance(web);

    // A Publication as LVH Yard stored it before Instance Priority: its Scope alone.
    consul.putValue("lvh-yard/publications/" + web, "local");

    awaitRoutedTo(web, terminal);
  }

  /** The remote Workstation's Instance, which this Workstation counts as in range. */
  private static final String TEAMMATE = "10.9.8.7:3000";

  /**
   * A Service of the remote Workstation, published with Yard Scope, whose one Instance has no
   * check, so it passes. Its address is in range here but reaches nothing.
   */
  private void passingRemoteService(String service) {
    String id =
        remote.register(service, Map.of("host", "10.9.8.7", "port", 3000)).get("id").asString();
    remote.deregisterCheckInConsul(id);
    remote.publish(service, "yard");
    await()
        .atMost(SWITCH_TIMEOUT)
        .until(() ->
            remote
                .service(service)
                .getBody()
                .get("instances")
                .valueStream()
                .allMatch(instance -> instance.get("health").asString().equals("passing")));
  }

  @Test
  void routeFallsBackToTheAliasWhenNoOwnInstancePassesAndComesBack() {
    String web = newServiceName("web");
    passingRemoteService(web);
    int port = newLanPort();
    listenOnLanAddress(port);
    register(web, Map.of("port", port, "check", Map.of("interval", "1s")));
    publish(web);
    put(
        AliasesComponentTest.aliasPath(web),
        Map.of("workstation", RemoteWorkstation.NAME, "service", web));
    String terminal = lanAddress() + ":" + port;
    awaitRoutedTo(web, terminal);

    stopListeningOnLanAddress(port);

    awaitRoutedTo(web, TEAMMATE);
    JsonNode routing = get(servicePath(web)).getBody().get("routing");
    assertThat(routing.get("kind").asString()).isEqualTo("alias");
    assertThat(summaryCard(getPage("/services/ws-test/" + web).getBody(), "routing"))
        .contains("Alias");

    listenOnLanAddress(port);

    awaitRoutedTo(web, terminal);
  }

  @Test
  void otherWorkstationsNeverUseThisWorkstationsAlias() {
    String web = newServiceName("web");
    passingRemoteService(web);
    int port = newLanPort();
    // Nothing listens on this port, so the Static Instance's check stays critical.
    register(web, Map.of("port", port, "check", Map.of("interval", "1s")));
    put(publicationPath(web), Map.of("scope", "yard"));
    put(
        AliasesComponentTest.aliasPath(web),
        Map.of("workstation", RemoteWorkstation.NAME, "service", web));

    awaitRoutedTo(web, TEAMMATE);
    remote.whileDashboardRunsWith(
        () ->
            awaitRoutedTo(
                () -> remote.traefikConfiguration().get("http"),
                web + "." + WORKSTATION + ".lvh.test",
                lanAddress() + ":" + port),
        "--lvh-yard.out-of-range.ranges=127.0.0.0/8,::1/128");
  }

  private void onRemoteThatReachesContainers(Runnable meanwhile) {
    remote.whileDashboardRunsWith(meanwhile, "--lvh-yard.out-of-range.ranges=127.0.0.0/8,::1/128");
  }

  private void awaitRoutedOnRemote(String service, String... addresses) {
    awaitRoutedTo(
        () -> remote.traefikConfiguration().get("http"),
        service + "." + WORKSTATION + ".lvh.test",
        addresses);
  }

  @Test
  void yardScopedServiceIsRoutedWithItsHomeWorkstationsOrderOnEveryWorkstation() {
    String web = newServiceName("web");
    staticInstance(web);
    String bound = boundInstance(web);

    put(publicationPath(web), Map.of("scope", "yard", "priority", List.of("bound", "static")));

    awaitRoutedTo(web, bound);
    onRemoteThatReachesContainers(() -> awaitRoutedOnRemote(web, bound));
  }
}
