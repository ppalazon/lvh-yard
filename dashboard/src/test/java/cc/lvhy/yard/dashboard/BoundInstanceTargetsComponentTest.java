package cc.lvhy.yard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import cc.lvhy.yard.dashboard.docker.Docker.Container;
import cc.lvhy.yard.dashboard.docker.FakeDocker;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * Where a Bound Instance is, by the Scope of its Service's Publication: loopback or the LAN address
 * for a Host Port on every address, and Docker's own address wherever it names one.
 */
class BoundInstanceTargetsComponentTest extends DashboardComponentTest {

  private static final Duration FOLLOW_TIMEOUT = Duration.ofSeconds(15);

  private static final String LOOPBACK = "127.0.0.1";

  private void scope(String service, String scope) {
    put(publicationPath(service), Map.of("scope", scope));
  }

  /** Each Instance of {@code service} as {@code host:port}, once {@code count} are registered. */
  private List<String> targets(String service, int count) {
    await()
        .atMost(FOLLOW_TIMEOUT)
        .until(() -> get(servicePath(service)).getBody().get("instances").size() == count);
    return targetsNow(service);
  }

  private List<String> targetsNow(String service) {
    return get(servicePath(service))
        .getBody()
        .get("instances")
        .valueStream()
        .map(instance -> instance.get("host").asString() + ":" + instance.get("port").asInt())
        .toList();
  }

  /** Where the Route of this Workstation's {@code service} sends requests. */
  private List<String> routeTargets(String service) {
    return TraefikConfigurationComponentTest.serverUrlsAt(
        get("/traefik/configuration").getBody().get("http"), service + ".lvh.test");
  }

  private JsonNode onlyInstance(String service) {
    return get(servicePath(service)).getBody().get("instances").get(0);
  }

  @Test
  void wildcardHostPortFollowsThePublicationThroughEveryChangeOfScopeOfALiveContainer() {
    String web = newServiceName("web");
    String name = newContainerName();
    Container container = startContainer(name, 80, true);
    int hostPort = hostPort(container, 80);
    bind(web, name, 80);

    assertThat(targets(web, 1)).containsExactly(lanAddress() + ":" + hostPort);
    String id = onlyInstance(web).get("id").asString();

    publish(web);
    assertThat(targetsNow(web)).containsExactly(LOOPBACK + ":" + hostPort);
    assertThat(routeTargets(web)).containsExactly("http://" + LOOPBACK + ":" + hostPort);

    scope(web, "yard");
    assertThat(targetsNow(web)).containsExactly(lanAddress() + ":" + hostPort);
    assertThat(routeTargets(web)).containsExactly("http://" + lanAddress() + ":" + hostPort);

    scope(web, "local");
    assertThat(targetsNow(web)).containsExactly(LOOPBACK + ":" + hostPort);
    assertThat(routeTargets(web)).containsExactly("http://" + LOOPBACK + ":" + hostPort);

    delete(publicationPath(web));
    assertThat(targetsNow(web)).containsExactly(lanAddress() + ":" + hostPort);

    JsonNode service = get(servicePath(web)).getBody();
    assertThat(service.get("instances")).hasSize(1);
    JsonNode instance = service.get("instances").get(0);
    assertThat(instance.get("id").asString()).isEqualTo(id);
    assertThat(instance.get("containerBinding").asString()).isEqualTo(name + ":80");
    assertThat(instance.get("container").asString()).isEqualTo(name);
    assertThat(instance.get("containerPort").asInt()).isEqualTo(80);
    assertThat(instance.get("hostPort").asInt()).isEqualTo(hostPort);
    assertThat(service.get("containerBindings")).hasSize(1);
  }

  @Test
  void explicitLoopbackBindingKeepsItsAddressUnderYardScopeAndWarns() {
    String web = newServiceName("web");
    String name = newContainerName();
    Container container = start(FakeDocker.container(name).publishedOn(LOOPBACK, 80));
    int hostPort = hostPort(container, 80);
    bind(web, name, 80);
    publish(web);

    scope(web, "yard");

    assertThat(targetsNow(web)).containsExactly(LOOPBACK + ":" + hostPort);
    JsonNode warnings = get(servicePath(web)).getBody().get("warnings");
    assertThat(warnings.valueStream().map(warning -> warning.get("kind").asString()))
        .containsExactly("loopback-instance");
  }

  @Test
  void explicitLanBindingKeepsItsAddressUnderLocalScope() {
    String web = newServiceName("web");
    String name = newContainerName();
    Container container = start(FakeDocker.container(name).publishedOn(lanAddress(), 80));
    int hostPort = hostPort(container, 80);
    bind(web, name, 80);

    publish(web);

    assertThat(targetsNow(web)).containsExactly(lanAddress() + ":" + hostPort);
    assertThat(routeTargets(web)).containsExactly("http://" + lanAddress() + ":" + hostPort);
  }

  @Test
  void containerPortWithNoHostPortKeepsTheContainerAddressUnderEveryScope() {
    String web = newServiceName("web");
    String name = newContainerName();
    Container container = startContainer(name, 80, false);
    String own = containerAddress(container) + ":80";
    bind(web, name, 80);
    assertThat(targets(web, 1)).containsExactly(own);

    publish(web);
    assertThat(targetsNow(web)).containsExactly(own);
    assertThat(routeTargets(web)).containsExactly("http://" + own);

    scope(web, "yard");
    assertThat(targetsNow(web)).containsExactly(own);
    assertThat(
            get(servicePath(web))
                .getBody()
                .get("warnings")
                .valueStream()
                .map(warning -> warning.get("kind").asString()))
        .containsExactly("unpublished-container-port");
  }

  @Test
  void hostNetworkContainerIsAtLoopbackUnderLocalScopeAndTheLanAddressUnderYardScope() {
    String web = newServiceName("web");
    String name = newContainerName();
    int port = startHostNetworkContainer(name);
    bind(web, name, port);
    assertThat(targets(web, 1)).containsExactly(lanAddress() + ":" + port);

    publish(web);
    assertThat(targetsNow(web)).containsExactly(LOOPBACK + ":" + port);
    assertThat(routeTargets(web)).containsExactly("http://" + LOOPBACK + ":" + port);

    scope(web, "yard");
    assertThat(targetsNow(web)).containsExactly(lanAddress() + ":" + port);
    assertThat(get(servicePath(web)).getBody().get("warnings")).isEmpty();
  }

  @Test
  void servicesFollowingOneContainerKeepTheirOwnTargets() {
    String local = newServiceName("local");
    String yard = newServiceName("yard");
    String unpublished = newServiceName("plain");
    String name = newContainerName();
    Container container = startContainer(name, 80, true);
    int hostPort = hostPort(container, 80);
    bind(local, name, 80);
    bind(yard, name, 80);
    bind(unpublished, name, 80);

    publish(local);
    scope(yard, "yard");

    assertThat(targetsNow(local)).containsExactly(LOOPBACK + ":" + hostPort);
    assertThat(targetsNow(yard)).containsExactly(lanAddress() + ":" + hostPort);
    assertThat(targetsNow(unpublished)).containsExactly(lanAddress() + ":" + hostPort);
  }

  @Test
  void eachContainerOfAComposeServiceUsesItsOwnMappingAndOtherProjectsAreLeftOut() {
    String web = newServiceName("web");
    String project = newComposeProject();
    Container wildcard =
        start(FakeDocker.container(newContainerName()).compose(project, "web").published(80));
    Container explicit =
        start(
            FakeDocker.container(newContainerName())
                .compose(project, "web")
                .publishedOn(lanAddress(), 80));
    Container internal =
        start(FakeDocker.container(newContainerName()).compose(project, "web").port(80));
    start(FakeDocker.container(newContainerName()).compose(project, "db").published(80));
    start(
        FakeDocker.container(newContainerName()).compose(newComposeProject(), "web").published(80));
    bindCompose(web, project, "web", 80);

    publish(web);

    assertThat(targets(web, 3))
        .containsExactlyInAnyOrder(
            LOOPBACK + ":" + hostPort(wildcard, 80),
            lanAddress() + ":" + hostPort(explicit, 80),
            containerAddress(internal) + ":80");

    scope(web, "yard");

    assertThat(targetsNow(web))
        .containsExactlyInAnyOrder(
            lanAddress() + ":" + hostPort(wildcard, 80),
            lanAddress() + ":" + hostPort(explicit, 80),
            containerAddress(internal) + ":80");
  }

  @Test
  void containerThatStartsAfterThePublicationUsesItsScope() {
    String web = newServiceName("web");
    String name = newContainerName();
    bind(web, name, 80);
    publish(web);

    Container container = startContainer(name, 80, true);

    await()
        .atMost(FOLLOW_TIMEOUT)
        .untilAsserted(() ->
            assertThat(targetsNow(web)).containsExactly(LOOPBACK + ":" + hostPort(container, 80)));
  }

  @Test
  void separateLoopbackAndLanMappingsAreChosenByScopeAndUnpublishedKeepsTheOriginalChoice() {
    String web = newServiceName("web");
    String name = newContainerName();
    Container container =
        start(
            FakeDocker.container(name).publishedOn(lanAddress(), 80).alsoPublishedOn(LOOPBACK, 80));
    String lan = lanAddress() + ":" + hostPortOn(container, 80, lanAddress());
    String loopback = LOOPBACK + ":" + hostPortOn(container, 80, LOOPBACK);
    bind(web, name, 80);
    assertThat(targets(web, 1)).containsExactly(lan);

    publish(web);
    assertThat(targetsNow(web)).containsExactly(loopback);
    assertThat(routeTargets(web)).containsExactly("http://" + loopback);
    assertThat(onlyInstance(web).get("hostPort").asInt())
        .isEqualTo(hostPortOn(container, 80, LOOPBACK));

    scope(web, "yard");
    assertThat(targetsNow(web)).containsExactly(lan);
    assertThat(routeTargets(web)).containsExactly("http://" + lan);
    assertThat(get(servicePath(web)).getBody().get("warnings")).isEmpty();

    scope(web, "local");
    assertThat(targetsNow(web)).containsExactly(loopback);

    delete(publicationPath(web));
    assertThat(targetsNow(web)).containsExactly(lan);
  }

  @Test
  void equallyRankedPreferredMappingsKeepTheirOrderAndAWildcardComesFirst() {
    String yard = newServiceName("yard");
    String local = newServiceName("local");
    String ties = newContainerName();
    Container tied =
        start(
            FakeDocker.container(ties)
                .publishedOn(LOOPBACK, 80)
                .alsoPublishedOn("10.9.8.7", 80)
                .alsoPublishedOn("10.9.8.8", 80)
                .alsoPublishedOn("127.0.0.2", 80));
    bind(yard, ties, 80);
    scope(yard, "yard");
    String firstLan = "10.9.8.7:" + hostPortOn(tied, 80, "10.9.8.7");
    assertThat(targets(yard, 1)).containsExactly(firstLan);
    assertThat(routeTargets(yard)).containsExactly("http://" + firstLan);
    bind(local, ties, 80);
    publish(local);
    assertThat(targets(local, 1)).containsExactly(LOOPBACK + ":" + hostPortOn(tied, 80, LOOPBACK));

    String name = newContainerName();
    Container withWildcard =
        start(
            FakeDocker.container(name)
                .publishedOn(LOOPBACK, 90)
                .alsoPublishedOn("10.9.8.7", 90)
                .alsoPublishedOn("0.0.0.0,::", 90));
    int wildcardPort = hostPortOn(withWildcard, 90, "0.0.0.0");
    String mixedYard = newServiceName("mixed-yard");
    String mixedLocal = newServiceName("mixed-local");
    bind(mixedYard, name, 90);
    bind(mixedLocal, name, 90);
    scope(mixedYard, "yard");
    publish(mixedLocal);
    assertThat(targets(mixedYard, 1)).containsExactly(lanAddress() + ":" + wildcardPort);
    assertThat(routeTargets(mixedYard))
        .containsExactly("http://" + lanAddress() + ":" + wildcardPort);
    assertThat(targets(mixedLocal, 1)).containsExactly(LOOPBACK + ":" + wildcardPort);
  }

  @Test
  void whenNoMappingSuitsTheScopeTheFirstOfAllMappingsIsUsedWithItsOwnPort() {
    String yard = newServiceName("yard");
    String local = newServiceName("local");
    String loopbackOnly = newContainerName();
    Container loopbacks =
        start(
            FakeDocker.container(loopbackOnly)
                .publishedOn("127.0.0.2", 80)
                .alsoPublishedOn(LOOPBACK, 80));
    bind(yard, loopbackOnly, 80);
    scope(yard, "yard");

    String expected = "127.0.0.2:" + hostPortOn(loopbacks, 80, "127.0.0.2");
    assertThat(targets(yard, 1)).containsExactly(expected);
    assertThat(
            get(servicePath(yard))
                .getBody()
                .get("warnings")
                .valueStream()
                .map(warning -> warning.get("kind").asString()))
        .containsExactly("loopback-instance");

    String lanOnly = newContainerName();
    Container lans =
        start(
            FakeDocker.container(lanOnly)
                .publishedOn("10.9.8.8", 80)
                .alsoPublishedOn(lanAddress(), 80));
    bind(local, lanOnly, 80);
    publish(local);
    assertThat(targets(local, 1)).containsExactly("10.9.8.8:" + hostPortOn(lans, 80, "10.9.8.8"));
  }

  @Test
  void eachContainerOfAComposeServiceChoosesFromItsOwnMappings() {
    String web = newServiceName("web");
    String project = newComposeProject();
    Container both =
        start(
            FakeDocker.container(newContainerName())
                .compose(project, "web")
                .publishedOn(lanAddress(), 80)
                .alsoPublishedOn(LOOPBACK, 80));
    Container lanOnly =
        start(
            FakeDocker.container(newContainerName())
                .compose(project, "web")
                .publishedOn(lanAddress(), 80));
    Container loopbackOnly =
        start(
            FakeDocker.container(newContainerName())
                .compose(project, "web")
                .publishedOn(LOOPBACK, 80));
    bindCompose(web, project, "web", 80);

    publish(web);
    assertThat(targets(web, 3))
        .containsExactlyInAnyOrder(
            LOOPBACK + ":" + hostPortOn(both, 80, LOOPBACK),
            lanAddress() + ":" + hostPortOn(lanOnly, 80, lanAddress()),
            LOOPBACK + ":" + hostPortOn(loopbackOnly, 80, LOOPBACK));

    scope(web, "yard");
    assertThat(targetsNow(web))
        .containsExactlyInAnyOrder(
            lanAddress() + ":" + hostPortOn(both, 80, lanAddress()),
            lanAddress() + ":" + hostPortOn(lanOnly, 80, lanAddress()),
            LOOPBACK + ":" + hostPortOn(loopbackOnly, 80, LOOPBACK));
  }

  @Test
  void servicesFollowingOneContainerWithSeveralMappingsChooseByTheirOwnScope() {
    String local = newServiceName("local");
    String yard = newServiceName("yard");
    String name = newContainerName();
    Container container =
        start(
            FakeDocker.container(name).publishedOn(lanAddress(), 80).alsoPublishedOn(LOOPBACK, 80));
    bind(local, name, 80);
    bind(yard, name, 80);

    publish(local);
    scope(yard, "yard");

    assertThat(targets(local, 1))
        .containsExactly(LOOPBACK + ":" + hostPortOn(container, 80, LOOPBACK));
    assertThat(targets(yard, 1))
        .containsExactly(lanAddress() + ":" + hostPortOn(container, 80, lanAddress()));
    assertThat(routeTargets(yard))
        .containsExactly("http://" + lanAddress() + ":" + hostPortOn(container, 80, lanAddress()));
  }

  /** The other Workstation's own Yard-Scope Instances, as its Routes here take them. */
  private void assertNoRemoteRouteButWarning(String service) {
    await()
        .atMost(FOLLOW_TIMEOUT)
        .untilAsserted(() -> {
          JsonNode remoteService =
              get("/api/services/" + RemoteWorkstation.NAME + "/" + service).getBody();
          assertThat(remoteService.get("instances")).hasSize(1);
          assertThat(remoteService.get("warnings").valueStream().map(w -> w.get("kind").asString()))
              .containsExactly("loopback-instance");
        });
    assertThat(
            TraefikConfigurationComponentTest.routedHostnames(
                get("/traefik/configuration").getBody().get("http")))
        .doesNotContain(service + "." + RemoteWorkstation.NAME + ".lvh.test");
  }

  @Test
  void explicitLoopbackMappingUnderYardScopeIsNotRoutedFromAnotherWorkstation() {
    String web = newServiceName("web");
    String name = newContainerName();
    start(FakeDocker.container(name).publishedOn(LOOPBACK, 80));
    remote.bind(web, name, 80);

    remote.publish(web, "yard");

    assertNoRemoteRouteButWarning(web);
  }

  @Test
  void noSuitableMappingUnderYardScopeIsNotRoutedFromAnotherWorkstation() {
    String web = newServiceName("web");
    String name = newContainerName();
    start(FakeDocker.container(name).publishedOn("127.0.0.2", 80).alsoPublishedOn(LOOPBACK, 80));
    remote.bind(web, name, 80);

    remote.publish(web, "yard");

    assertNoRemoteRouteButWarning(web);
  }
}
