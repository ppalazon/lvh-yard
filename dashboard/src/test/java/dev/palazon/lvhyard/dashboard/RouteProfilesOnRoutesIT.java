package dev.palazon.lvhyard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * A Publication that names Route Profiles of this Workstation: what the API shows for it, and the
 * middlewares its Route gets in the Traefik configuration.
 */
class RouteProfilesOnRoutesIT extends DashboardIT {

  private static final JsonMapper JSON = new JsonMapper();

  private static final JsonNode PROFILES =
      JSON.readTree(
          """
          {"behind-https": {"headers": {"customRequestHeaders": {"X-Forwarded-Proto": "https"}}},
           "team-login": {"basicAuth": {"users": ["ana:$apr1$H6uskkkW$IgXLP6ewTrSuBkTrqE8wj/"]}},
           "office-only": {"ipAllowList": {"sourceRange": ["192.168.1.0/24"]}},
           "protected": {"chain": {"middlewares": ["office-only", "team-login"]}}}
          """);

  @AfterEach
  void removeTheProfiles() throws Exception {
    removeAllRouteProfiles();
  }

  /** The message of {@code service}'s warning of {@code kind}, or {@code null}. */
  private static String warning(JsonNode service, String kind) {
    return service
        .get("warnings")
        .valueStream()
        .filter(warning -> warning.get("kind").asString().equals(kind))
        .map(warning -> warning.get("message").asString())
        .findFirst()
        .orElse(null);
  }

  /** The severity of {@code service}'s warning of {@code kind}, or {@code null}. */
  private static String severity(JsonNode service, String kind) {
    return service
        .get("warnings")
        .valueStream()
        .filter(warning -> warning.get("kind").asString().equals(kind))
        .map(warning -> warning.get("severity").asString())
        .findFirst()
        .orElse(null);
  }

  private static org.awaitility.core.ConditionFactory await() {
    return org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(10));
  }

  @Test
  void profileGoneFromConsulStaysOnTheRouteAndWarns() throws Exception {
    putRouteProfiles(PROFILES);
    String web = newServiceName("web");
    register(web, Map.of("host", "10.1.2.3", "port", 8080));
    put(publicationPath(web), Map.of("profiles", List.of("behind-https", "team-login")));

    // Straight from Consul: the API refuses to remove a profile that a Publication names.
    consulContainer.execInContainer("consul", "kv", "delete", "lvh-yard/route-profiles/team-login");

    JsonNode http = get("/traefik/configuration").getBody().get("http");
    assertThat(http.get("routers").get(web).get("middlewares").toString())
        .isEqualTo("[\"ws-test_behind-https\",\"ws-test_team-login\"]");
    assertThat(http.get("middlewares").has("ws-test_team-login")).isFalse();
    assertThat(warning(get(servicePath(web)).getBody(), "unknown-route-profile"))
        .contains("team-login")
        .doesNotContain("behind-https");
    assertThat(severity(get(servicePath(web)).getBody(), "unknown-route-profile"))
        .isEqualTo("error");
    assertThat(
            div(
                getPage("/services/ws-test/" + web).getBody(),
                "The Route Profile team-login does not exist"))
        .contains("role=\"alert\"")
        .contains("data-variant=\"destructive\"");
  }

  @Test
  void chainMemberGoneFromConsulStaysInTheChainSoTraefikRefusesTheRoute() throws Exception {
    putRouteProfiles(PROFILES);
    String web = newServiceName("web");
    register(web, Map.of("host", "10.1.2.3", "port", 8080));
    put(publicationPath(web), Map.of("profiles", List.of("protected")));

    // Straight from Consul: the API refuses to remove a profile that a chain names.
    consulContainer.execInContainer("consul", "kv", "delete", "lvh-yard/route-profiles/team-login");

    JsonNode middlewares = get("/traefik/configuration").getBody().get("http").get("middlewares");
    assertThat(middlewares.get("ws-test_protected"))
        .isEqualTo(
            JSON.readTree(
                """
                {"chain": {"middlewares": ["ws-test_office-only", "ws-test_team-login"]}}
                """));
    assertThat(middlewares.has("ws-test_office-only")).isTrue();
    assertThat(middlewares.has("ws-test_team-login")).isFalse();
  }

  @Test
  void remotePublicationNamingAProfileItsHomeLacksStaysOnTheRouteAndWarns() {
    String api = newServiceName("api");
    remote.register(api, Map.of("host", "10.9.8.7", "port", 9090));
    try {
      // Straight to Consul: its dashboard refuses a profile it does not have.
      remote.putConsulValue(
          "lvh-yard/publications/" + api,
          """
          {"scope": "yard", "priority": ["static", "bound", "alias"], "profiles": ["gone"]}
          """);

      String router = api + "." + RemoteWorkstation.NAME;
      await()
          .untilAsserted(() ->
              assertThat(
                      get("/traefik/configuration")
                          .getBody()
                          .get("http")
                          .get("routers")
                          .path(router)
                          .path("middlewares")
                          .toString())
                  .isEqualTo("[\"" + RemoteWorkstation.NAME + "_gone\"]"));
      JsonNode service = get("/api/services/" + RemoteWorkstation.NAME + "/" + api).getBody();
      assertThat(warning(service, "unknown-route-profile")).contains("gone");
      assertThat(getPage("/services/" + RemoteWorkstation.NAME + "/" + api).getBody())
          .contains("The Route Profile gone does not exist");
    } finally {
      remote.unpublish(api);
    }
  }
}
