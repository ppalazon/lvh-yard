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

/** What Traefik's HTTP provider reads from the dashboard. */
class TraefikConfigurationIT extends DashboardIT {

  private JsonNode router(String name) {
    return get("/traefik/configuration").getBody().get("http").get("routers").get(name);
  }

  /** The hostname of every Route, from its {@code Host(`...`)} rule. */
  static List<String> routedHostnames(JsonNode http) {
    return http.get("routers")
        .valueStream()
        .map(router -> hostnameOf(router.get("rule").asString()))
        .toList();
  }

  /** Where the Route for {@code hostname} sends requests. */
  static List<String> serverUrlsAt(JsonNode http, String hostname) {
    JsonNode router =
        http.get("routers")
            .valueStream()
            .filter(candidate -> hostnameOf(candidate.get("rule").asString()).equals(hostname))
            .findFirst()
            .orElseThrow(() -> new AssertionError("No Route for " + hostname + " in " + http));
    assertThat(router.get("entryPoints").get(0).asString()).isEqualTo("web");
    return serverUrls(http, router.get("service").asString());
  }

  private static String hostnameOf(String rule) {
    return rule.substring("Host(`".length(), rule.length() - "`)".length());
  }

  private static List<String> serverUrls(JsonNode http, String service) {
    return http.get("services")
        .get(service)
        .get("loadBalancer")
        .get("servers")
        .valueStream()
        .map(server -> server.get("url").asString())
        .toList();
  }

  @Test
  void remoteServiceWithYardScopeIsRoutedAtItsWorkstationsHostname() {
    String api = newServiceName("api");
    remote.register(api, Map.of("host", "10.9.8.7", "port", 9090));
    remote.register(api, Map.of("host", "10.9.8.8", "port", 9090));

    remote.publish(api, "yard");

    JsonNode http = get("/traefik/configuration").getBody().get("http");
    assertThat(serverUrlsAt(http, api + "." + RemoteWorkstation.NAME + ".lvh.test"))
        .containsExactly("http://10.9.8.7:9090", "http://10.9.8.8:9090");
    assertThat(routedHostnames(http)).doesNotContain(api + ".lvh.test");
  }

  @Test
  void remoteServiceWithLocalScopeHasNoRoute() {
    String api = newServiceName("api");
    remote.register(api, Map.of("host", "10.9.8.7", "port", 9090));

    remote.publish(api, "local");

    assertThat(routedHostnames(get("/traefik/configuration").getBody().get("http")))
        .noneMatch(hostname -> hostname.startsWith(api + "."));
  }

  @Test
  void remoteServiceWithTheSameNameAsAnOwnServiceGetsARouteOfItsOwn() {
    String api = newServiceName("api");
    register(api, Map.of("host", "10.1.2.3", "port", 8080));
    publish(api);
    remote.register(api, Map.of("host", "10.9.8.7", "port", 9090));

    remote.publish(api, "yard");

    JsonNode http = get("/traefik/configuration").getBody().get("http");
    assertThat(serverUrlsAt(http, api + ".lvh.test")).containsExactly("http://10.1.2.3:8080");
    assertThat(serverUrlsAt(http, api + "." + RemoteWorkstation.NAME + ".lvh.test"))
        .containsExactly("http://10.9.8.7:9090");
  }

  @Test
  void routesOfAnUnreachableWorkstationAreDroppedAndComeBackByThemselves() {
    String web = newServiceName("web");
    register(web, Map.of("host", "10.1.2.3", "port", 8080));
    publish(web);
    String api = newServiceName("api");
    remote.register(api, Map.of("host", "10.9.8.7", "port", 9090));
    remote.publish(api, "yard");
    String remoteHostname = api + "." + RemoteWorkstation.NAME + ".lvh.test";

    remote.stopAnswering();
    try {
      JsonNode http =
          await()
              .atMost(Duration.ofSeconds(60))
              .until(
                  () -> get("/traefik/configuration").getBody().get("http"),
                  configuration -> !routedHostnames(configuration).contains(remoteHostname));

      assertThat(routedHostnames(http)).contains(web + ".lvh.test", "yard.lvh.test");
    } finally {
      remote.answerAgain();
    }

    await()
        .atMost(Duration.ofSeconds(60))
        .until(
            () -> routedHostnames(get("/traefik/configuration").getBody().get("http")),
            hostnames -> hostnames.contains(remoteHostname));
  }

  @Test
  void remoteInstanceOnLoopbackGetsNoRoute() {
    String api = newServiceName("api");
    remote.register(api, Map.of("host", "127.0.0.1", "port", 9090));
    remote.register(api, Map.of("host", "10.9.8.7", "port", 9090));

    remote.publish(api, "yard");

    assertThat(
            serverUrlsAt(
                get("/traefik/configuration").getBody().get("http"),
                api + "." + RemoteWorkstation.NAME + ".lvh.test"))
        .containsExactly("http://10.9.8.7:9090");
  }

  @Test
  void remoteInstanceOnADockerNetworkGetsNoRoute() {
    String api = newServiceName("api");
    remote.register(api, Map.of("host", "172.18.0.5", "port", 9090));
    remote.register(api, Map.of("host", "10.9.8.7", "port", 9090));

    remote.publish(api, "yard");

    assertThat(
            serverUrlsAt(
                get("/traefik/configuration").getBody().get("http"),
                api + "." + RemoteWorkstation.NAME + ".lvh.test"))
        .containsExactly("http://10.9.8.7:9090");
  }

  @Test
  void remoteServiceWithOnlyLoopbackInstancesHasNoRoute() {
    String api = newServiceName("api");
    remote.register(api, Map.of("host", "127.0.0.1", "port", 9090));
    remote.register(api, Map.of("host", "localhost", "port", 9091));

    remote.publish(api, "yard");

    assertThat(routedHostnames(get("/traefik/configuration").getBody().get("http")))
        .noneMatch(hostname -> hostname.startsWith(api + "."));
  }

  @Test
  void workstationWithAFaultLosesItsRoutesAndTheOthersKeepTheirs() {
    String web = newServiceName("web");
    register(web, Map.of("host", "10.1.2.3", "port", 8080));
    publish(web);
    String api = newServiceName("api");
    remote.register(api, Map.of("host", "10.9.8.7", "port", 9090));
    remote.publish(api, "yard");
    String remoteHostname = api + "." + RemoteWorkstation.NAME + ".lvh.test";
    // A Scope this dashboard does not know, as a newer LVH Yard might write: reading ws-remote
    // fails.
    String broken = "lvh-yard/publications/" + newServiceName("broken");

    remote.putConsulValue(broken, "world");
    try {
      ResponseEntity<JsonNode> response = get("/traefik/configuration");

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(routedHostnames(response.getBody().get("http")))
          .contains(web + ".lvh.test", "yard.lvh.test")
          .doesNotContain(remoteHostname);
    } finally {
      remote.deleteConsulValue(broken);
    }

    assertThat(routedHostnames(get("/traefik/configuration").getBody().get("http")))
        .contains(remoteHostname);
  }
}
