package dev.palazon.lvhyard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * The {@code alias} kind of the Instance Priority: an Alias with the same name as a Service of this
 * Workstation is its last fallback, on this Workstation's own short hostname only.
 */
class InstancePriorityAliasIT extends InstancePriorityTestSupportIT {

  /** The remote Workstation's Instance, which this Workstation counts as in range. */
  private static final String TEAMMATE = "10.9.8.7:3000";

  @Test
  void routeFallsBackToTheAliasWhenNoOwnInstancePassesAndComesBack() {
    String web = newServiceName("web");
    passingRemoteService(web);
    int port = newLanPort();
    listenOnLanAddress(port);
    register(web, Map.of("port", port, "check", Map.of("interval", "1s")));
    publish(web);
    put(AliasesIT.aliasPath(web), Map.of("workstation", RemoteWorkstation.NAME, "service", web));
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
  void publishedServiceWhoseInstancesAreGoneIsRoutedToTheAlias() {
    String web = newServiceName("web");
    passingRemoteService(web);
    String id = register(web, Map.of("host", "10.1.2.3", "port", 8080)).get("id").asString();
    publish(web);
    put(AliasesIT.aliasPath(web), Map.of("workstation", RemoteWorkstation.NAME, "service", web));

    delete(instancesPath(web) + "/" + id);

    awaitRoutedTo(web, TEAMMATE);
    JsonNode alias =
        get(AliasesIT.aliasesPath())
            .getBody()
            .valueStream()
            .filter(found -> found.get("name").asString().equals(web))
            .findFirst()
            .orElseThrow();
    assertThat(alias.get("state").asString()).isEqualTo("fallback");
    assertThat(alias.get("reason").asString()).startsWith("In use now");
    JsonNode reachability = get(ReachabilityIT.reachabilityPath(WORKSTATION, web)).getBody();
    assertThat(reachability.get("routing").get("kind").asString()).isEqualTo("alias");
    assertThat(reachability.get("alias").get("workstation").asString())
        .isEqualTo(RemoteWorkstation.NAME);
    assertThat(reachability.get("alias").get("service").asString()).isEqualTo(web);
    assertThat(getPage("/services/ws-test/" + web + "/reachability").getBody())
        .contains(
            "The Route sends requests to the Alias&#39;s Remote Service: "
                + web
                + " on "
                + RemoteWorkstation.NAME
                + ".");
  }

  @Test
  void otherWorkstationsNeverUseThisWorkstationsAlias() {
    String web = newServiceName("web");
    passingRemoteService(web);
    int port = newLanPort();
    // Nothing listens on this port, so the Static Instance's check stays critical.
    register(web, Map.of("port", port, "check", Map.of("interval", "1s")));
    put(publicationPath(web), Map.of("scope", "yard"));
    put(AliasesIT.aliasPath(web), Map.of("workstation", RemoteWorkstation.NAME, "service", web));

    awaitRoutedTo(web, TEAMMATE);
    remote.whileDashboardRunsWith(
        () ->
            awaitRoutedTo(
                () -> remote.traefikConfiguration().get("http"),
                web + "." + WORKSTATION + ".lvh.test",
                lanAddress() + ":" + port),
        "--lvh-yard.out-of-range.ranges=127.0.0.0/8,::1/128");
  }

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
}
