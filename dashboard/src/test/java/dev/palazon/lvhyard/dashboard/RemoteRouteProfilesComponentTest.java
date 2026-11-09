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
 * Route Profiles of a Service published to the Yard by {@value RemoteWorkstation#NAME}, as the test
 * Workstation routes it: on the Service's remote hostname and on its page.
 */
class RemoteRouteProfilesComponentTest extends DashboardComponentTest {

  private static final JsonMapper JSON = new JsonMapper();

  private static final JsonNode REMOTE_PROFILES =
      JSON.readTree(
          """
          {"team-login": {"basicAuth": {"users": ["ana:$apr1$H6uskkkW$IgXLP6ewTrSuBkTrqE8wj/"]}},
           "office-only": {"ipAllowList": {"sourceRange": ["192.168.1.0/24"]}},
           "protected": {"chain": {"middlewares": ["office-only", "team-login"]}}}
          """);

  @AfterEach
  void removeTheProfiles() throws Exception {
    remote.removeAllRouteProfiles();
    removeAllRouteProfiles();
  }

  /**
   * A Service of {@value RemoteWorkstation#NAME} with an Instance, published to the Yard with
   * {@code profiles}. Waits until the test Workstation routes it.
   */
  private String sharedRemoteServiceWithProfiles(String prefix, List<String> profiles) {
    String service = newServiceName(prefix);
    remote.register(service, Map.of("host", "10.9.8.7", "port", 3000));
    remote.publish(service, Map.of("scope", "yard", "profiles", profiles));
    await()
        .untilAsserted(() ->
            assertThat(
                    get("/traefik/configuration")
                        .getBody()
                        .get("http")
                        .get("routers")
                        .has(service + "." + RemoteWorkstation.NAME))
                .isTrue());
    return service;
  }

  private static org.awaitility.core.ConditionFactory await() {
    return org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(10));
  }

  @Test
  void remoteServiceRouteAppliesTheProfilesOfItsHomeWorkstation() throws Exception {
    remote.putRouteProfiles(REMOTE_PROFILES);
    String admin = sharedRemoteServiceWithProfiles("admin", List.of("protected"));

    JsonNode http = get("/traefik/configuration").getBody().get("http");

    String router = admin + "." + RemoteWorkstation.NAME;
    assertThat(http.get("routers").get(router).get("middlewares").toString())
        .isEqualTo("[\"ws-remote_protected\"]");
    assertThat(http.get("middlewares").get("ws-remote_protected"))
        .isEqualTo(
            JSON.readTree(
                """
                {"chain": {"middlewares": ["ws-remote_office-only", "ws-remote_team-login"]}}
                """));
    assertThat(http.get("middlewares").get("ws-remote_team-login").get("basicAuth")).isNotNull();
    assertThat(http.get("middlewares").has("ws-remote_office-only")).isTrue();
  }

  @Test
  void profilesOfTheSameNameOnTwoWorkstationsNeverClash() throws Exception {
    remote.putRouteProfiles(REMOTE_PROFILES);
    putRouteProfiles(
        JSON.readTree(
            """
            {"team-login": {"basicAuth": {"users": ["bob:$apr1$H6uskkkW$IgXLP6ewTrSuBkTrqE8wj/"]}}}
            """));
    String web = newServiceName("web");
    register(web, Map.of("host", "10.1.2.3", "port", 8080));
    put(publicationPath(web), Map.of("profiles", List.of("team-login")));
    sharedRemoteServiceWithProfiles("admin", List.of("team-login"));

    JsonNode middlewares = get("/traefik/configuration").getBody().get("http").get("middlewares");

    assertThat(middlewares.get("ws-test_team-login").toString()).contains("bob:");
    assertThat(middlewares.get("ws-remote_team-login").toString()).contains("ana:");
  }

  @Test
  void remoteServicePageShowsItsProfileNames() throws Exception {
    remote.putRouteProfiles(REMOTE_PROFILES);
    String admin = sharedRemoteServiceWithProfiles("admin", List.of("protected", "team-login"));

    String page = getPage("/services/" + RemoteWorkstation.NAME + "/" + admin).getBody();

    assertThat(page)
        .contains("Route Profiles")
        .contains("1. protected")
        .contains("2. team-login")
        .doesNotContain("aria-label=\"Remove protected\"")
        .doesNotContain("aria-label=\"Add ");
  }

  @Test
  void changeOnTheHomeWorkstationReachesTheRemoteConfigurationAndItsChains() throws Exception {
    remote.putRouteProfiles(REMOTE_PROFILES);
    String admin = sharedRemoteServiceWithProfiles("admin", List.of("team-login"));
    String chained = sharedRemoteServiceWithProfiles("chained", List.of("protected"));

    remote.putRouteProfiles(
        JSON.readTree(
            """
            {"team-login": {"basicAuth": {"users": ["carla:$apr1$H6uskkkW$IgXLP6ewTrSuBkTrqE8wj/"]}}}
            """));

    await()
        .untilAsserted(() ->
            assertThat(
                    get("/traefik/configuration")
                        .getBody()
                        .get("http")
                        .get("middlewares")
                        .get("ws-remote_team-login")
                        .toString())
                .contains("carla:")
                .doesNotContain("ana:"));
    JsonNode http = get("/traefik/configuration").getBody().get("http");
    assertThat(http.get("routers").get(admin + "." + RemoteWorkstation.NAME).toString())
        .contains("ws-remote_team-login");
    assertThat(http.get("routers").get(chained + "." + RemoteWorkstation.NAME).toString())
        .contains("ws-remote_protected");
  }

  @Test
  void changeToAMemberReachesTheRemoteChainWithNoWriteToTheChain() throws Exception {
    remote.putRouteProfiles(REMOTE_PROFILES);
    sharedRemoteServiceWithProfiles("chained", List.of("protected"));
    String chainKey = "lvh-yard/route-profiles/protected";
    long chainWritten = remote.consulModifyIndex(chainKey);

    remote.putRouteProfiles(
        JSON.readTree(
            """
            {"team-login": {"basicAuth": {"users": ["carla:$apr1$H6uskkkW$IgXLP6ewTrSuBkTrqE8wj/"]}}}
            """));

    await()
        .untilAsserted(() ->
            assertThat(
                    get("/traefik/configuration")
                        .getBody()
                        .get("http")
                        .get("middlewares")
                        .get("ws-remote_team-login")
                        .toString())
                .contains("carla:")
                .doesNotContain("ana:"));
    assertThat(remote.consulModifyIndex(chainKey)).isEqualTo(chainWritten);
    assertThat(JSON.readTree(remote.consulValue(chainKey)))
        .isEqualTo(
            JSON.readTree("{\"chain\": {\"middlewares\": [\"office-only\", \"team-login\"]}}"));
  }
}
