package dev.palazon.lvhyard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * A Publication that names Route Profiles of this Workstation: what the API shows for it, and the
 * middlewares its Route gets in the Traefik configuration.
 */
class RouteProfilesOnRoutesTest extends DashboardTest {

  private static final JsonMapper JSON = new JsonMapper();

  private static final String PROFILES =
      """
      http:
        middlewares:
          behind-https:
            headers:
              customRequestHeaders:
                X-Forwarded-Proto: https
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
  void removeTheFile() throws IOException {
    Files.deleteIfExists(TestcontainersConfiguration.ROUTE_PROFILES);
    await().untilAsserted(() -> assertThat(profileNames()).isEmpty());
  }

  @Test
  void publicationNamesItsProfilesAndItsRouteListsThemInOrder() throws Exception {
    writeProfiles(PROFILES);
    String web = newServiceName("web");
    register(web, Map.of("host", "10.1.2.3", "port", 8080));

    JsonNode published =
        put(publicationPath(web), Map.of("profiles", List.of("team-login", "behind-https")))
            .getBody();

    assertThat(published.get("profiles").toString()).isEqualTo("[\"team-login\",\"behind-https\"]");
    JsonNode http = get("/traefik/configuration").getBody().get("http");
    assertThat(http.get("routers").get(web).get("middlewares").toString())
        .isEqualTo("[\"ws-test_team-login\",\"ws-test_behind-https\"]");
    assertThat(http.get("middlewares").get("ws-test_behind-https"))
        .isEqualTo(
            JSON.readTree(
                """
                {"headers": {"customRequestHeaders": {"X-Forwarded-Proto": "https"}}}
                """));
    assertThat(http.get("middlewares").get("ws-test_team-login").get("basicAuth")).isNotNull();
  }

  @Test
  void chainComesWithItsMembersRenamedTheSameWay() throws Exception {
    writeProfiles(PROFILES);
    String web = newServiceName("web");
    register(web, Map.of("host", "10.1.2.3", "port", 8080));

    put(publicationPath(web), Map.of("profiles", List.of("protected")));

    JsonNode http = get("/traefik/configuration").getBody().get("http");
    assertThat(http.get("routers").get(web).get("middlewares").toString())
        .isEqualTo("[\"ws-test_protected\"]");
    assertThat(http.get("middlewares").get("ws-test_protected"))
        .isEqualTo(
            JSON.readTree(
                """
                {"chain": {"middlewares": ["ws-test_office-only", "ws-test_team-login"]}}
                """));
    assertThat(http.get("middlewares").has("ws-test_office-only")).isTrue();
    assertThat(http.get("middlewares").has("ws-test_team-login")).isTrue();
  }

  @Test
  void publishingWithoutProfilesKeepsThemAndAnEmptyListRemovesThem() throws Exception {
    writeProfiles(PROFILES);
    String web = newServiceName("web");
    register(web, Map.of("host", "10.1.2.3", "port", 8080));
    put(publicationPath(web), Map.of("profiles", List.of("behind-https")));

    JsonNode kept = put(publicationPath(web), Map.of("scope", "yard")).getBody();

    assertThat(kept.get("profiles").toString()).isEqualTo("[\"behind-https\"]");

    JsonNode cleared = put(publicationPath(web), Map.of("profiles", List.of())).getBody();

    assertThat(cleared.has("profiles")).isFalse();
    assertThat(cleared.get("publication").get("scope").asString()).isEqualTo("yard");
    JsonNode router = get("/traefik/configuration").getBody().get("http").get("routers").get(web);
    assertThat(router.has("middlewares")).isFalse();
  }

  @Test
  void unknownProfileIsRefusedWithTheNamesThatExist() throws Exception {
    writeProfiles(PROFILES);
    String web = newServiceName("web");
    register(web, Map.of("host", "10.1.2.3", "port", 8080));

    ResponseEntity<JsonNode> refused =
        put(publicationPath(web), Map.of("profiles", List.of("behind-https", "team-logn")));

    assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(refused.getBody().get("detail").asString())
        .contains("team-logn")
        .contains("behind-https, office-only, protected, team-login");
    assertThat(get(servicePath(web)).getBody().has("publication")).isFalse();
  }

  @Test
  void profileRemovedFromTheFileStaysOnTheRouteAndWarns() throws Exception {
    writeProfiles(PROFILES);
    String web = newServiceName("web");
    register(web, Map.of("host", "10.1.2.3", "port", 8080));
    put(publicationPath(web), Map.of("profiles", List.of("behind-https", "team-login")));

    writeProfiles(
        """
        http:
          middlewares:
            behind-https:
              headers:
                customRequestHeaders:
                  X-Forwarded-Proto: https
        """);
    await().untilAsserted(() -> assertThat(profileNames()).containsExactly("behind-https"));

    JsonNode http = get("/traefik/configuration").getBody().get("http");
    assertThat(http.get("routers").get(web).get("middlewares").toString())
        .isEqualTo("[\"ws-test_behind-https\",\"ws-test_team-login\"]");
    assertThat(http.get("middlewares").has("ws-test_team-login")).isFalse();
    assertThat(warning(get(servicePath(web)).getBody(), "unknown-route-profile"))
        .contains("team-login")
        .doesNotContain("behind-https");
    assertThat(getPage("/services/ws-test/" + web).getBody())
        .contains("The Route Profile team-login does not exist");
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

  @Test
  void authProfileWarnsThatADirectConnectionSkipsIt() throws Exception {
    writeProfiles(PROFILES);
    String login = newServiceName("login");
    String chained = newServiceName("chained");
    String headers = newServiceName("headers");
    for (String service : List.of(login, chained, headers)) {
      register(service, Map.of("host", "10.1.2.3", "port", 8080));
    }

    JsonNode withLogin =
        put(publicationPath(login), Map.of("profiles", List.of("team-login"))).getBody();
    JsonNode withChain =
        put(publicationPath(chained), Map.of("profiles", List.of("protected"))).getBody();
    JsonNode withHeaders =
        put(publicationPath(headers), Map.of("profiles", List.of("behind-https"))).getBody();

    assertThat(warning(withLogin, "route-profile-auth"))
        .contains("team-login")
        .contains("10.1.2.3:8080")
        .contains("check its users itself");
    assertThat(warning(withChain, "route-profile-auth")).contains("protected");
    assertThat(warning(withHeaders, "route-profile-auth")).isNull();
  }

  @Test
  void servicePagePicksProfilesFromTheFileAndShowsTheAuthWarning() throws Exception {
    writeProfiles(PROFILES);
    String web = newServiceName("web");
    register(web, Map.of("host", "10.1.2.3", "port", 8080));
    publish(web);
    String page = "/services/ws-test/" + web;
    assertThat(getPage(page).getBody())
        .contains("Route Profiles")
        .contains("aria-label=\"Add team-login\"")
        .contains("name=\"profiles\" value=\"team-login\"")
        .contains("aria-label=\"Add protected\"");

    ResponseEntity<String> response =
        postForm(page + "/publish", Map.of("profiles", "behind-https,team-login"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER);
    assertThat(get(servicePath(web)).getBody().get("profiles").toString())
        .isEqualTo("[\"behind-https\",\"team-login\"]");
    assertThat(getPage(page).getBody())
        .contains("aria-label=\"Move team-login up\"")
        .contains("name=\"profiles\" value=\"team-login,behind-https\"")
        .contains("aria-label=\"Remove behind-https\"")
        .contains("name=\"profiles\" value=\"team-login\"")
        .doesNotContain("aria-label=\"Add team-login\"")
        .contains("asks who the user is only on the hostname");
  }

  @Test
  void servicePageRefusesAnUnknownProfile() throws Exception {
    writeProfiles(PROFILES);
    String web = newServiceName("web");
    register(web, Map.of("host", "10.1.2.3", "port", 8080));

    ResponseEntity<String> response =
        postForm("/services/ws-test/" + web + "/publish", Map.of("profiles", "nope"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
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

  /** Writes the file and waits until the dashboard took it. */
  private void writeProfiles(String yaml) throws IOException {
    Files.writeString(TestcontainersConfiguration.ROUTE_PROFILES, yaml);
    await().untilAsserted(() -> assertThat(profileNames()).isNotEmpty());
  }

  private List<String> profileNames() {
    return get("/api/workstation/route-profiles")
        .getBody()
        .get("profiles")
        .valueStream()
        .map(profile -> profile.get("name").asString())
        .toList();
  }

  private static org.awaitility.core.ConditionFactory await() {
    return org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(10));
  }
}
