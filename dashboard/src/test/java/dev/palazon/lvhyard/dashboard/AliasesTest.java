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

/** Aliases: short hostnames this Workstation gives to Remote Services, for its own use only. */
class AliasesTest extends DashboardTest {

  @Test
  void aliasOfAYardScopedRemoteServiceIsRoutedToItsInstances() {
    String grafana = newServiceName("grafana");
    remote.register(grafana, Map.of("host", "10.9.8.7", "port", 3000));
    remote.publish(grafana, "yard");

    ResponseEntity<JsonNode> response =
        put(aliasPath(grafana), Map.of("workstation", RemoteWorkstation.NAME, "service", grafana));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().get("name").asString()).isEqualTo(grafana);
    assertThat(response.getBody().get("hostname").asString()).isEqualTo(grafana + ".lvh.test");
    assertThat(response.getBody().get("state").asString()).isEqualTo("active");
    assertThat(
            TraefikConfigurationTest.serverUrlsAt(
                get("/traefik/configuration").getBody().get("http"), grafana + ".lvh.test"))
        .containsExactly("http://10.9.8.7:3000");
  }

  @Test
  void aliasNameFollowsTheServiceNameRules() {
    String grafana = sharedRemoteService("grafana");

    for (String name : List.of("Grafana", "-grafana", "graf.ana", "yard")) {
      ResponseEntity<JsonNode> response =
          put(aliasPath(name), Map.of("workstation", RemoteWorkstation.NAME, "service", grafana));

      assertThat(response.getStatusCode()).as(name).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(response.getBody().get("detail").asString()).as(name).contains(name);
    }
    assertThat(
            get(aliasesPath()).getBody().valueStream().map(alias -> alias.get("name").asString()))
        .doesNotContain("yard", "Grafana");
  }

  @Test
  void aliasNamesARemoteServiceThatExists() {
    String web = newServiceName("web");
    register(web, Map.of("host", "10.1.2.3", "port", 8080));
    String alias = newServiceName("alias");

    ResponseEntity<JsonNode> own =
        put(aliasPath(alias), Map.of("workstation", WORKSTATION, "service", web));
    ResponseEntity<JsonNode> noWorkstation =
        put(aliasPath(alias), Map.of("workstation", "ws-nowhere", "service", web));
    ResponseEntity<JsonNode> noService =
        put(
            aliasPath(alias),
            Map.of("workstation", RemoteWorkstation.NAME, "service", newServiceName("nothing")));

    assertThat(own.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(own.getBody().get("detail").asString()).contains("Remote Service");
    assertThat(noWorkstation.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(noWorkstation.getBody().get("detail").asString()).contains("ws-nowhere");
    assertThat(noService.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(noService.getBody().get("detail").asString()).contains(RemoteWorkstation.NAME);
    assertThat(
            get(aliasesPath()).getBody().valueStream().map(found -> found.get("name").asString()))
        .doesNotContain(alias);
  }

  @Test
  void ownPublishedServiceWithTheSameNameWinsAndHidesTheAlias() {
    String grafana = sharedRemoteService("grafana");
    put(aliasPath(grafana), Map.of("workstation", RemoteWorkstation.NAME, "service", grafana));
    register(grafana, Map.of("host", "10.1.2.3", "port", 3000));

    publish(grafana);

    JsonNode alias = alias(grafana);
    assertThat(alias.get("state").asString()).isEqualTo("hidden");
    assertThat(alias.get("reason").asString())
        .contains("this Workstation's published Service " + grafana);
    assertThat(
            TraefikConfigurationTest.serverUrlsAt(
                get("/traefik/configuration").getBody().get("http"), grafana + ".lvh.test"))
        .containsExactly("http://10.1.2.3:3000");
  }

  @Test
  void ownUnpublishedServiceWithTheSameNameLeavesTheAliasActive() {
    String grafana = sharedRemoteService("grafana");
    register(grafana, Map.of("host", "10.1.2.3", "port", 3000));

    put(aliasPath(grafana), Map.of("workstation", RemoteWorkstation.NAME, "service", grafana));

    assertThat(alias(grafana).get("state").asString()).isEqualTo("active");
    assertThat(
            TraefikConfigurationTest.serverUrlsAt(
                get("/traefik/configuration").getBody().get("http"), grafana + ".lvh.test"))
        .containsExactly("http://10.9.8.7:3000");
  }

  @Test
  void aliasOfARemoteServiceWithLocalScopeHasNoRouteAndSaysWhy() {
    String grafana = sharedRemoteService("grafana");
    put(aliasPath(grafana), Map.of("workstation", RemoteWorkstation.NAME, "service", grafana));

    remote.publish(grafana, "local");

    JsonNode alias = alias(grafana);
    assertThat(alias.get("state").asString()).isEqualTo("not-shared");
    assertThat(alias.get("reason").asString()).contains("Local Scope");
    assertThat(routedHostnames(get("/traefik/configuration").getBody().get("http")))
        .doesNotContain(grafana + ".lvh.test");
  }

  @Test
  void aliasOfAnUnpublishedRemoteServiceHasNoRouteAndSaysWhy() {
    String grafana = sharedRemoteService("grafana");
    put(aliasPath(grafana), Map.of("workstation", RemoteWorkstation.NAME, "service", grafana));

    remote.unpublish(grafana);

    JsonNode alias = alias(grafana);
    assertThat(alias.get("state").asString()).isEqualTo("not-shared");
    assertThat(alias.get("reason").asString()).contains("not published");
    assertThat(routedHostnames(get("/traefik/configuration").getBody().get("http")))
        .doesNotContain(grafana + ".lvh.test");
  }

  @Test
  void aliasOfARemoteServiceThatIsGoneSaysWhy() {
    String grafana = newServiceName("grafana");
    String id =
        remote.register(grafana, Map.of("host", "10.9.8.7", "port", 3000)).get("id").asString();
    put(aliasPath(grafana), Map.of("workstation", RemoteWorkstation.NAME, "service", grafana));

    remote.remove(grafana, id);

    JsonNode alias = alias(grafana);
    assertThat(alias.get("state").asString()).isEqualTo("missing");
    assertThat(alias.get("reason").asString()).contains("has no Service " + grafana);
  }

  @Test
  void aliasOfAnUnreachableWorkstationsServiceHasNoRouteAndComesBack() {
    String grafana = sharedRemoteService("grafana");
    put(aliasPath(grafana), Map.of("workstation", RemoteWorkstation.NAME, "service", grafana));

    remote.stopAnswering();
    try {
      await()
          .atMost(Duration.ofSeconds(60))
          .until(
              () -> routedHostnames(get("/traefik/configuration").getBody().get("http")),
              hostnames -> !hostnames.contains(grafana + ".lvh.test"));
      JsonNode alias = alias(grafana);
      assertThat(alias.get("state").asString()).isEqualTo("unreachable");
      assertThat(alias.get("reason").asString()).contains("unreachable");
    } finally {
      remote.answerAgain();
    }

    await()
        .atMost(Duration.ofSeconds(60))
        .until(
            () -> routedHostnames(get("/traefik/configuration").getBody().get("http")),
            hostnames -> hostnames.contains(grafana + ".lvh.test"));
  }

  @Test
  void noOtherWorkstationGetsARouteForTheAlias() {
    String grafana = sharedRemoteService("grafana");
    String alias = newServiceName("short");

    put(aliasPath(alias), Map.of("workstation", RemoteWorkstation.NAME, "service", grafana));

    assertThat(routedHostnames(get("/traefik/configuration").getBody().get("http")))
        .contains(alias + ".lvh.test");
    assertThat(routedHostnames(remote.traefikConfiguration().get("http")))
        .doesNotContain(alias + ".lvh.test");
  }

  @Test
  void aliasOfARemoteServiceWithNoInstanceInRangeHasNoRouteAndSaysWhy() {
    String grafana = newServiceName("grafana");
    remote.register(grafana, Map.of("host", "127.0.0.1", "port", 3000));
    remote.publish(grafana, "yard");

    put(aliasPath(grafana), Map.of("workstation", RemoteWorkstation.NAME, "service", grafana));

    JsonNode alias = alias(grafana);
    assertThat(alias.get("state").asString()).isEqualTo("unavailable");
    assertThat(alias.get("reason").asString()).contains("no Instance");
    assertThat(routedHostnames(get("/traefik/configuration").getBody().get("http")))
        .doesNotContain(grafana + ".lvh.test");
  }

  @Test
  void removedAliasLosesItsRoute() {
    String grafana = sharedRemoteService("grafana");
    put(aliasPath(grafana), Map.of("workstation", RemoteWorkstation.NAME, "service", grafana));

    ResponseEntity<JsonNode> response = delete(aliasPath(grafana));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(
            get(aliasesPath()).getBody().valueStream().map(alias -> alias.get("name").asString()))
        .doesNotContain(grafana);
    assertThat(routedHostnames(get("/traefik/configuration").getBody().get("http")))
        .doesNotContain(grafana + ".lvh.test");
  }

  @Test
  void removingAnUnknownAliasIsNotFound() {
    String alias = newServiceName("nothing");

    ResponseEntity<JsonNode> response = delete(aliasPath(alias));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody().get("detail").asString()).contains(alias);
  }

  /** The Alias called {@code name}, as the list of this Workstation's Aliases has it. */
  private JsonNode alias(String name) {
    return get(aliasesPath())
        .getBody()
        .valueStream()
        .filter(alias -> alias.get("name").asString().equals(name))
        .findFirst()
        .orElseThrow(() -> new AssertionError("No Alias " + name));
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

  static String aliasesPath() {
    return "/api/workstation/aliases";
  }

  static String aliasPath(String alias) {
    return "/api/workstation/aliases/" + alias;
  }

  static List<String> routedHostnames(JsonNode http) {
    return TraefikConfigurationTest.routedHostnames(http);
  }
}
