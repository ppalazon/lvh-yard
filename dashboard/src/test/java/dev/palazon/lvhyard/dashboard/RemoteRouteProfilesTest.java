package dev.palazon.lvhyard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Route Profiles of a Service published to the Yard by {@value RemoteWorkstation#NAME}, as the test
 * Workstation routes it: on the Service's remote hostname, on an Alias, and on its page.
 */
class RemoteRouteProfilesTest extends DashboardTest {

  private static final JsonMapper JSON = new JsonMapper();

  private static final String PROFILES_PATH = "/api/workstation/route-profiles";

  private static final String REMOTE_PROFILES =
      """
      http:
        middlewares:
          team-login:
            basicAuth:
              users:
                - "ana:$apr1$H6uskkkW$IgXLP6ewTrSuBkTrqE8wj/"
          office-only:
            ipAllowList:
              sourceRange: [192.168.1.0/24]
          protected:
            chain:
              middlewares: [office-only, team-login]
      """;

  @AfterEach
  void removeTheFiles() throws IOException {
    Files.deleteIfExists(RemoteWorkstation.ROUTE_PROFILES);
    Files.deleteIfExists(TestcontainersConfiguration.ROUTE_PROFILES);
    await()
        .untilAsserted(() -> {
          assertThat(profileNames(remote.get(PROFILES_PATH).getBody())).isEmpty();
          assertThat(profileNames(get(PROFILES_PATH).getBody())).isEmpty();
        });
  }

  @Test
  void remoteServiceRouteAppliesTheProfilesOfItsHomeWorkstation() throws Exception {
    writeRemoteProfiles(REMOTE_PROFILES);
    String admin = sharedRemoteServiceWithProfiles("admin", List.of("protected"));

    JsonNode http = get("/traefik/configuration").getBody().get("http");

    String router = admin + "." + RemoteWorkstation.NAME;
    assertThat(http.get("routers").get(router).get("middlewares").toString())
        .isEqualTo("[\"ws-remote-protected\"]");
    assertThat(http.get("middlewares").get("ws-remote-protected"))
        .isEqualTo(
            JSON.readTree(
                """
                {"chain": {"middlewares": ["ws-remote-office-only", "ws-remote-team-login"]}}
                """));
    assertThat(http.get("middlewares").get("ws-remote-team-login").get("basicAuth")).isNotNull();
    assertThat(http.get("middlewares").has("ws-remote-office-only")).isTrue();
  }

  @Test
  void profilesOfTheSameNameOnTwoWorkstationsNeverClash() throws Exception {
    writeRemoteProfiles(REMOTE_PROFILES);
    Files.writeString(
        TestcontainersConfiguration.ROUTE_PROFILES,
        """
        http:
          middlewares:
            team-login:
              basicAuth:
                users:
                  - "bob:$apr1$H6uskkkW$IgXLP6ewTrSuBkTrqE8wj/"
        """);
    await()
        .untilAsserted(() -> assertThat(profileNames(get(PROFILES_PATH).getBody())).isNotEmpty());
    String web = newServiceName("web");
    register(web, Map.of("host", "10.1.2.3", "port", 8080));
    put(publicationPath(web), Map.of("profiles", List.of("team-login")));
    sharedRemoteServiceWithProfiles("admin", List.of("team-login"));

    JsonNode middlewares = get("/traefik/configuration").getBody().get("http").get("middlewares");

    assertThat(middlewares.get("ws-test-team-login").toString()).contains("bob:");
    assertThat(middlewares.get("ws-remote-team-login").toString()).contains("ana:");
  }

  @Test
  void aliasAppliesTheProfilesOfItsRemoteService() throws Exception {
    writeRemoteProfiles(REMOTE_PROFILES);
    String admin = sharedRemoteServiceWithProfiles("admin", List.of("team-login"));
    String alias = newServiceName("alias");

    put(
        AliasesTest.aliasPath(alias),
        Map.of("workstation", RemoteWorkstation.NAME, "service", admin));

    JsonNode http = get("/traefik/configuration").getBody().get("http");
    assertThat(http.get("routers").get("alias_" + alias).get("middlewares").toString())
        .isEqualTo("[\"ws-remote-team-login\"]");
    assertThat(http.get("middlewares").has("ws-remote-team-login")).isTrue();
  }

  @Test
  void remoteServicePageShowsItsProfileNames() throws Exception {
    writeRemoteProfiles(REMOTE_PROFILES);
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
  void changeInTheHomeFileReachesTheRemoteConfigurationWithinSeconds() throws Exception {
    writeRemoteProfiles(REMOTE_PROFILES);
    sharedRemoteServiceWithProfiles("admin", List.of("team-login"));

    Files.writeString(RemoteWorkstation.ROUTE_PROFILES, REMOTE_PROFILES.replace("ana:", "carla:"));

    await()
        .untilAsserted(() ->
            assertThat(
                    get("/traefik/configuration")
                        .getBody()
                        .get("http")
                        .get("middlewares")
                        .get("ws-remote-team-login")
                        .toString())
                .contains("carla:"));
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

  /** Writes ws-remote's file and waits until its dashboard took it. */
  private void writeRemoteProfiles(String yaml) throws IOException {
    Files.writeString(RemoteWorkstation.ROUTE_PROFILES, yaml);
    await()
        .untilAsserted(
            () -> assertThat(profileNames(remote.get(PROFILES_PATH).getBody())).isNotEmpty());
  }

  private static List<String> profileNames(JsonNode listed) {
    return listed
        .get("profiles")
        .valueStream()
        .map(profile -> profile.get("name").asString())
        .toList();
  }

  private static org.awaitility.core.ConditionFactory await() {
    return org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(10));
  }
}
