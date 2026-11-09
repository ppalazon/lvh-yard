package dev.palazon.lvhyard.dashboard;

import static dev.palazon.lvhyard.dashboard.TraefikConfigurationComponentTest.serverUrlsAt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.palazon.lvhyard.dashboard.consul.Consul.Registration;
import dev.palazon.lvhyard.dashboard.docker.Docker.Container;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

  static List<String> ids(JsonNode sources) {
    return sources.valueStream().map(source -> source.get("id").asString()).toList();
  }

  /** The IDs of {@code service}'s Instance Sources, in order. */
  List<String> sourceIds(String service) {
    return ids(get(servicePath(service)).getBody().get("sources"));
  }

  @Test
  void routeMovesToTheSecondStaticInstanceWhenTheFirstStopsAndBackWhenItStartsAgain() {
    String web = newServiceName("web");
    String first = staticInstance(web);
    String second = staticInstance(web);
    publish(web);
    awaitRoutedTo(web, first);

    stopListeningOnLanAddress(port(first));

    awaitRoutedTo(web, second);

    listenOnLanAddress(port(first));

    awaitRoutedTo(web, first);
  }

  @Test
  void newSourceGoesToTheEndAndNeverTakesOverTheRoute() {
    String web = newServiceName("web");
    String terminal = staticInstance(web);
    publish(web);
    awaitRoutedTo(web, terminal);
    String staticId = sourceIds(web).getFirst();

    String bindingId = bind(web, newContainerName(), 80).get("id").asString();
    boundInstance(web);

    assertThat(sourceIds(web)).startsWith(staticId).contains(bindingId);
    assertThat(sourceIds(web).indexOf(bindingId)).isGreaterThan(0);
    awaitRoutedTo(web, terminal);
  }

  @Test
  void apiAndServicePageShowTheSourceInUseAndTheSourcesWaiting() {
    String web = newServiceName("web");
    staticInstance(web);
    boundInstance(web);

    publish(web);

    await()
        .atMost(SWITCH_TIMEOUT)
        .untilAsserted(() -> {
          JsonNode service = get(servicePath(web)).getBody();
          List<String> sources = ids(service.get("sources"));
          assertThat(service.get("instancePriority").asBoolean()).isTrue();
          assertThat(service.get("routing").get("source").asString()).isEqualTo(sources.getFirst());
          assertThat(service.get("routing").get("kind").asString()).isEqualTo("static");
          assertThat(texts(service.get("routing").get("waiting"))).containsExactly(sources.get(1));
          String page = getPage("/services/ws-test/" + web).getBody();
          assertThat(summaryCard(page, "routing"))
              .contains("Static Instance")
              .contains("1 source waiting");
          assertThat(sourceItem(page, sources.getFirst())).contains("data-in-use");
          assertThat(sourceItem(page, sources.get(1))).doesNotContain("data-in-use");
        });
  }

  /** The list item of one Instance Source on the Service page. */
  static String sourceItem(String page, String id) {
    int start = page.indexOf("data-source=\"" + id + "\"");
    assertThat(start).as("Instance Source %s on the page", id).isNotNegative();
    int end = page.indexOf("data-source=", start + 1);
    return page.substring(start, end < 0 ? page.indexOf("</ol>", start) : end);
  }

  @Test
  void settingTheOrderMovesTheRoute() {
    String web = newServiceName("web");
    String terminal = staticInstance(web);
    String bound = boundInstance(web);
    publish(web);
    awaitRoutedTo(web, terminal);
    List<String> before = sourceIds(web);

    ResponseEntity<JsonNode> response =
        put(instancePriorityPath(web), Map.of("order", List.of(before.get(1))));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(ids(response.getBody().get("sources")))
        .containsExactly(before.get(1), before.get(0));
    awaitRoutedTo(web, bound);
    JsonNode read = get(instancePriorityPath(web)).getBody();
    assertThat(read.get("on").asBoolean()).isTrue();
    assertThat(ids(read.get("sources"))).containsExactly(before.get(1), before.get(0));
  }

  @Test
  void offSharesTheRouteAndTheOrderAndToggleStayOverUnpublishAndPublish() {
    String web = newServiceName("web");
    String first = staticInstance(web);
    String second = staticInstance(web);
    publish(web);
    String secondId = sourceIds(web).get(1);
    put(instancePriorityPath(web), Map.of("order", List.of(secondId)));
    awaitRoutedTo(web, second);

    put(instancePriorityPath(web), Map.of("on", false));

    awaitRoutedTo(web, first, second);
    assertThat(get(servicePath(web)).getBody().get("routing").get("off").asBoolean()).isTrue();

    delete(publicationPath(web));
    publish(web);

    JsonNode service = get(servicePath(web)).getBody();
    assertThat(service.get("instancePriority").asBoolean()).isFalse();
    assertThat(ids(service.get("sources")).getFirst()).isEqualTo(secondId);
    awaitRoutedTo(web, first, second);

    put(instancePriorityPath(web), Map.of("on", true));

    awaitRoutedTo(web, second);
  }

  @Test
  void orderThatNamesAnUnknownOrRepeatedSourceIsRefused() {
    String web = newServiceName("web");
    String id = register(web, Map.of("host", "10.1.2.3", "port", 8080)).get("id").asString();

    assertThat(put(instancePriorityPath(web), Map.of("order", List.of("nothing"))).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(put(instancePriorityPath(web), Map.of("order", List.of(id, id))).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(
            put(instancePriorityPath(newServiceName("missing")), Map.of("on", false))
                .getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void newForeignInstanceGoesToTheEnd() {
    String web = newServiceName("web");
    String terminal = staticInstance(web);
    publish(web);
    awaitRoutedTo(web, terminal);
    String foreign = "foreign-" + UUID.randomUUID();
    // With no check, the Foreign Instance passes at once.
    consul.register(
        new Registration(foreign, web, "10.1.2.3", newLanPort(), List.of(), Map.of(), null));
    try {
      String staticId = sourceIds(web).getFirst();

      assertThat(sourceIds(web)).containsExactly(staticId, foreign);
      awaitRoutedTo(web, terminal);

      String bindingId = bind(web, newContainerName(), 80).get("id").asString();

      assertThat(sourceIds(web)).containsExactly(staticId, foreign, bindingId);
    } finally {
      consul.deregister(foreign);
    }
  }

  @Test
  void editedStaticInstanceKeepsItsPlace() {
    String web = newServiceName("web");
    String first = register(web, Map.of("host", "10.1.2.3", "port", 8080)).get("id").asString();
    String second = register(web, Map.of("host", "10.1.2.3", "port", 8081)).get("id").asString();

    put(instancesPath(web) + "/" + first, Map.of("host", "10.1.2.3", "port", 8082));

    assertThat(sourceIds(web)).containsExactly(first, second);
  }

  @Test
  void removedSourceLeavesTheOrderAndComesBackAtTheEnd() {
    String web = newServiceName("web");
    String container = newContainerName();
    String bindingId = bind(web, container, 80).get("id").asString();
    String staticId = register(web, Map.of("host", "10.1.2.3", "port", 8080)).get("id").asString();
    assertThat(sourceIds(web)).containsExactly(bindingId, staticId);

    delete(containerBindingsPath(web) + "/" + bindingId);
    bind(web, container, 80);

    assertThat(sourceIds(web)).containsExactly(staticId, bindingId);
  }

  @Test
  void idInTheStoredOrderWhoseSourceIsGoneIsSkipped() {
    String web = newServiceName("web");
    String first = register(web, Map.of("host", "10.1.2.3", "port", 8080)).get("id").asString();
    String second = register(web, Map.of("host", "10.1.2.3", "port", 8081)).get("id").asString();

    consul.putValue(
        "lvh-yard/services/" + web, "{\"order\":[\"gone\",\"" + second + "\",\"" + first + "\"]}");

    assertThat(sourceIds(web)).containsExactly(second, first);
  }

  @Test
  void unpublishedServiceHasItsSourcesButNoRouting() {
    String web = newServiceName("web");
    staticInstance(web);

    JsonNode service = get(servicePath(web)).getBody();

    assertThat(service.get("sources")).hasSize(1);
    assertThat(service.has("routing")).isFalse();
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
  void otherWorkstationsSkipThisWorkstationsAliasEvenWhenItIsFirst() {
    String web = newServiceName("web");
    passingRemoteService(web);
    int port = newLanPort();
    // Nothing listens on this port, so the Static Instance's check stays critical.
    register(web, Map.of("port", port, "check", Map.of("interval", "1s")));
    put(publicationPath(web), Map.of("scope", "yard"));
    put(
        AliasesComponentTest.aliasPath(web),
        Map.of("workstation", RemoteWorkstation.NAME, "service", web));
    put(instancePriorityPath(web), Map.of("order", List.of(RemoteWorkstation.NAME + "/" + web)));

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
    put(publicationPath(web), Map.of("scope", "yard"));

    put(instancePriorityPath(web), Map.of("order", List.of(sourceIds(web).getLast())));

    awaitRoutedTo(web, bound);
    onRemoteThatReachesContainers(() -> awaitRoutedOnRemote(web, bound));
  }

  @Test
  void yardServiceReadFromItsHomeWorkstationFollowsItsOrderAndHasNoAliasSources() {
    String api = newServiceName("api");
    String first = passingRemoteInstance(api, 3000);
    String second = passingRemoteInstance(api, 3001);
    remote.publish(api, "yard");
    remote.addAlias(api, WORKSTATION, newServiceName("gone"));
    remote.setInstancePriority(api, Map.of("order", List.of(second)));
    assertThat(
            remote
                .service(api)
                .getBody()
                .get("sources")
                .valueStream()
                .map(source -> source.get("kind").asString()))
        .as("its Home Workstation lists the Alias")
        .contains("alias");

    await()
        .atMost(SWITCH_TIMEOUT)
        .untilAsserted(() -> {
          JsonNode service = get("/api/services/" + RemoteWorkstation.NAME + "/" + api).getBody();
          assertThat(ids(service.get("sources"))).containsExactly(second, first);
          assertThat(service.get("instancePriority").asBoolean()).isTrue();
          assertThat(service.get("routing").get("source").asString()).isEqualTo(second);
        });
  }

  /** A check-less, so passing, Instance of the remote Workstation at 10.9.8.7, and its ID. */
  private String passingRemoteInstance(String service, int port) {
    String id =
        remote.register(service, Map.of("host", "10.9.8.7", "port", port)).get("id").asString();
    remote.deregisterCheckInConsul(id);
    return id;
  }
}
