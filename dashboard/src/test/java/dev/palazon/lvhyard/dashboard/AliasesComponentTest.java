package dev.palazon.lvhyard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/** Aliases: Instance Sources of a Service that give the Instances of a Remote Service. */
class AliasesComponentTest extends DashboardComponentTest {

  static String aliasesPath(String service) {
    return servicePath(service) + "/aliases";
  }

  static String aliasPath(String service, String workstation, String remoteService) {
    return aliasesPath(service) + "/" + workstation + "/" + remoteService;
  }

  /**
   * A Service of the remote Workstation, published with Yard Scope, at an address this Workstation
   * can reach.
   */
  private String sharedRemoteService(String prefix) {
    String service = newServiceName(prefix);
    remote.register(service, Map.of("host", "10.9.8.7", "port", 3000));
    remote.publish(service, "yard");
    return service;
  }

  private ResponseEntity<JsonNode> addAlias(String service, String remoteService) {
    return post(
        aliasesPath(service),
        Map.of("workstation", RemoteWorkstation.NAME, "service", remoteService));
  }

  private JsonNode source(String service, String id) {
    return get(servicePath(service))
        .getBody()
        .get("sources")
        .valueStream()
        .filter(source -> source.get("id").asString().equals(id))
        .findFirst()
        .orElseThrow(() -> new AssertionError(service + " has no source " + id));
  }

  @Test
  void aliasIsAnInstanceSourceThatGivesTheInstancesOfItsRemoteService() {
    String grafana = sharedRemoteService("grafana");
    String web = newServiceName("web");
    create(web);

    ResponseEntity<JsonNode> response = addAlias(web, grafana);

    String id = RemoteWorkstation.NAME + "/" + grafana;
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody().get("id").asString()).isEqualTo(id);
    assertThat(response.getBody().get("state").asString()).isEqualTo("active");
    JsonNode source = source(web, id);
    assertThat(source.get("kind").asString()).isEqualTo("alias");
    assertThat(source.get("instances").size()).isEqualTo(1);
  }

  @Test
  void serviceCanHaveManyAliasesAndEachGoesToTheEndOfTheOrder() {
    String first = sharedRemoteService("first");
    String second = sharedRemoteService("second");
    String web = newServiceName("web");
    register(web, Map.of("host", "10.1.2.3", "port", 8080));

    addAlias(web, first);
    addAlias(web, second);

    List<String> ids =
        InstancePriorityComponentTest.ids(get(servicePath(web)).getBody().get("sources"));
    assertThat(ids).hasSize(3);
    assertThat(ids.subList(1, 3))
        .containsExactly(
            RemoteWorkstation.NAME + "/" + first, RemoteWorkstation.NAME + "/" + second);
    assertThat(
            get(aliasesPath(web))
                .getBody()
                .valueStream()
                .map(alias -> alias.get("service").asString()))
        .containsExactly(first, second);
  }

  @Test
  void secondAliasToTheSameRemoteServiceIsRefused() {
    String grafana = sharedRemoteService("grafana");
    String web = newServiceName("web");
    create(web);
    addAlias(web, grafana);

    ResponseEntity<JsonNode> again = addAlias(web, grafana);

    assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(again.getBody().get("detail").asString()).contains(grafana);
    assertThat(get(aliasesPath(web)).getBody().size()).isEqualTo(1);
  }

  @Test
  void aliasToAServiceOfThisWorkstationIsRefused() {
    String own = newServiceName("own");
    register(own, Map.of("host", "10.1.2.3", "port", 8080));
    put(publicationPath(own), Map.of("scope", "yard"));
    String web = newServiceName("web");
    create(web);

    ResponseEntity<JsonNode> response =
        post(aliasesPath(web), Map.of("workstation", WORKSTATION, "service", own));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().get("field").asString()).isEqualTo("workstation");
    assertThat(response.getBody().get("detail").asString()).contains("Remote Service");
    assertThat(get(aliasesPath(web)).getBody().size()).isZero();
  }

  @Test
  void namesThatAreNotOneDnsLabelAreRefusedAtTheirField() {
    String web = newServiceName("web");
    create(web);

    ResponseEntity<JsonNode> badWorkstation =
        post(aliasesPath(web), Map.of("workstation", "WS_B", "service", "grafana"));
    ResponseEntity<JsonNode> badService =
        post(aliasesPath(web), Map.of("workstation", RemoteWorkstation.NAME, "service", "-web"));

    assertThat(badWorkstation.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(badWorkstation.getBody().get("field").asString()).isEqualTo("workstation");
    assertThat(badService.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(badService.getBody().get("field").asString()).isEqualTo("service");
  }

  @Test
  void onlyARemoteServiceWithYardScopeCanBeTheTargetOfANewAlias() {
    String local = newServiceName("local");
    remote.register(local, Map.of("host", "10.9.8.7", "port", 3000));
    remote.publish(local, "local");
    String unpublished = newServiceName("unpublished");
    remote.register(unpublished, Map.of("host", "10.9.8.7", "port", 3000));
    String web = newServiceName("web");
    create(web);

    ResponseEntity<JsonNode> toLocal = addAlias(web, local);
    ResponseEntity<JsonNode> toUnpublished = addAlias(web, unpublished);
    ResponseEntity<JsonNode> toNothing = addAlias(web, newServiceName("nothing"));
    ResponseEntity<JsonNode> toNowhere =
        post(aliasesPath(web), Map.of("workstation", "ws-nowhere", "service", "grafana"));

    assertThat(toLocal.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(toLocal.getBody().get("detail").asString()).contains("Local Scope");
    assertThat(toUnpublished.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(toUnpublished.getBody().get("field").asString()).isEqualTo("service");
    assertThat(toNothing.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(toNothing.getBody().get("field").asString()).isEqualTo("service");
    assertThat(toNowhere.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(toNowhere.getBody().get("field").asString()).isEqualTo("workstation");
    assertThat(get(aliasesPath(web)).getBody().size()).isZero();
  }

  @Test
  void aliasOfAServiceYouHaveNotCreatedIsNotFound() {
    String grafana = sharedRemoteService("grafana");
    String web = newServiceName("web");

    ResponseEntity<JsonNode> response = addAlias(web, grafana);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(get(servicePath(web)).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void createMakesAMissingServiceWithLocalScopeAndKeepsAnExistingPublication() {
    String grafana = sharedRemoteService("grafana");
    String fresh = newServiceName("fresh");
    String shared = newServiceName("shared");
    register(shared, Map.of("host", "10.1.2.3", "port", 8080));
    put(publicationPath(shared), Map.of("scope", "yard"));

    ResponseEntity<JsonNode> created =
        post(
            aliasesPath(fresh),
            Map.of("workstation", RemoteWorkstation.NAME, "service", grafana, "create", true));
    ResponseEntity<JsonNode> existing =
        post(
            aliasesPath(shared),
            Map.of("workstation", RemoteWorkstation.NAME, "service", grafana, "create", true));

    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    JsonNode service = get(servicePath(fresh)).getBody();
    assertThat(service.get("publication").get("scope").asString()).isEqualTo("local");
    assertThat(InstancePriorityComponentTest.ids(service.get("sources")))
        .containsExactly(RemoteWorkstation.NAME + "/" + grafana);
    assertThat(existing.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(get(servicePath(shared)).getBody().get("publication").get("scope").asString())
        .isEqualTo("yard");
  }

  @Test
  void aliasStaysAfterItsTargetLosesYardScopeAndThenGivesNoInstancesAndSaysWhy() {
    String grafana = sharedRemoteService("grafana");
    String web = newServiceName("web");
    create(web);
    addAlias(web, grafana);

    remote.publish(grafana, "local");

    JsonNode alias = get(aliasesPath(web)).getBody().get(0);
    assertThat(alias.get("state").asString()).isEqualTo("not-shared");
    assertThat(alias.get("reason").asString()).contains("Local Scope");
    assertThat(source(web, RemoteWorkstation.NAME + "/" + grafana).get("instances").size())
        .isZero();
  }

  @Test
  void aliasStaysWhileItsWorkstationIsUnreachableAndGivesInstancesWhenItAnswersAgain() {
    String grafana = sharedRemoteService("grafana");
    String web = newServiceName("web");
    create(web);
    addAlias(web, grafana);
    String id = RemoteWorkstation.NAME + "/" + grafana;

    remote.stopAnswering();
    try {
      await()
          .atMost(Duration.ofSeconds(60))
          .untilAsserted(() -> {
            JsonNode alias = get(aliasesPath(web)).getBody().get(0);
            assertThat(alias.get("state").asString()).isEqualTo("unreachable");
            assertThat(alias.get("reason").asString()).contains("unreachable");
          });
      assertThat(source(web, id).get("instances").size()).isZero();
    } finally {
      remote.answerAgain();
    }

    await()
        .atMost(Duration.ofSeconds(60))
        .until(() -> source(web, id).get("instances").size() == 1);
  }

  @Test
  void removedAliasLeavesTheOrderAndTheListAndAnUnknownOneIsNotFound() {
    String grafana = sharedRemoteService("grafana");
    String web = newServiceName("web");
    create(web);
    addAlias(web, grafana);

    ResponseEntity<JsonNode> removed = delete(aliasPath(web, RemoteWorkstation.NAME, grafana));
    ResponseEntity<JsonNode> again = delete(aliasPath(web, RemoteWorkstation.NAME, grafana));

    assertThat(removed.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(again.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(again.getBody().get("detail").asString()).contains(grafana);
    assertThat(get(aliasesPath(web)).getBody().size()).isZero();
    assertThat(get(servicePath(web)).getBody().get("sources").size()).isZero();
  }

  @Test
  void yardServiceWithAnAliasAboveAnotherSourceHasARiskWarning() {
    String grafana = sharedRemoteService("grafana");
    String web = newServiceName("web");
    register(web, Map.of("host", "10.1.2.3", "port", 8080));
    addAlias(web, grafana);
    put(publicationPath(web), Map.of("scope", "yard"));
    assertThat(warningKinds(web)).doesNotContain("alias-above-source");

    put(
        instancePriorityPath(web),
        Map.of("order", List.of(RemoteWorkstation.NAME + "/" + grafana)));

    assertThat(warningKinds(web)).contains("alias-above-source");
    JsonNode warning =
        get(servicePath(web))
            .getBody()
            .get("warnings")
            .valueStream()
            .filter(found -> found.get("kind").asString().equals("alias-above-source"))
            .findFirst()
            .orElseThrow();
    assertThat(warning.get("severity").asString()).isEqualTo("risk");

    put(publicationPath(web), Map.of("scope", "local"));

    assertThat(warningKinds(web)).doesNotContain("alias-above-source");
  }

  private List<String> warningKinds(String service) {
    return get(servicePath(service))
        .getBody()
        .get("warnings")
        .valueStream()
        .map(warning -> warning.get("kind").asString())
        .toList();
  }

  @Test
  void workstationLevelAliasesAndTheirShortHostnamesAreGone() {
    String grafana = sharedRemoteService("grafana");
    String web = newServiceName("web");
    create(web);
    addAlias(web, grafana);

    assertThat(get("/api/workstation/aliases").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(
            TraefikConfigurationComponentTest.routedHostnames(
                get("/traefik/configuration").getBody().get("http")))
        .doesNotContain(grafana + ".lvh.test", web + ".lvh.test");
  }
}
