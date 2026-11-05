package dev.palazon.lvhyard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.Container.ExecResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The test Workstation's Route Profiles, as the API adds, lists and removes them, and as its Consul
 * KV holds them for the other Workstations.
 */
class RouteProfilesTest extends DashboardTest {

  private static final JsonMapper JSON = new JsonMapper();

  @AfterEach
  void removeTheProfiles() throws Exception {
    removeAllRouteProfiles();
  }

  @Test
  void addedProfileIsListedWithItsTypeAndStoredInConsul() throws Exception {
    ResponseEntity<JsonNode> added =
        put(
            ROUTE_PROFILES_PATH + "/added-headers",
            json(
                """
                {"headers": {"customRequestHeaders": {"X-Forwarded-Proto": "https"}}}
                """));

    assertThat(added.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(get(ROUTE_PROFILES_PATH).getBody())
        .isEqualTo(
            json("[{\"name\": \"added-headers\", \"type\": \"headers\", \"services\": []}]"));
    assertThat(JSON.readTree(kv("added-headers")))
        .isEqualTo(
            json(
                """
                {"headers": {"customRequestHeaders": {"X-Forwarded-Proto": "https"}}}
                """));
  }

  @Test
  void oneProfileIsReadWithItsDefinitionInTraefiksFormat() {
    putRouteProfiles(json("{\"compressed\": {\"compress\": {}}}"));

    ResponseEntity<JsonNode> read = get(ROUTE_PROFILES_PATH + "/compressed");

    assertThat(read.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(read.getBody())
        .isEqualTo(
            json(
                """
                {"name": "compressed", "type": "compress", "services": [],
                 "definition": {"compress": {}}}
                """));
    assertThat(get(ROUTE_PROFILES_PATH + "/missing").getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void listNamesTheServicesWhosePublicationsNameTheProfile() {
    putRouteProfiles(json("{\"used-compress\": {\"compress\": {}}}"));
    String web = newServiceName("web");
    String api = newServiceName("api");
    for (String service : List.of(web, api)) {
      register(service, Map.of("host", "10.1.2.3", "port", 8080));
      put(publicationPath(service), Map.of("profiles", List.of("used-compress")));
    }

    // Other test classes in this JVM add Route Profiles of their own.
    JsonNode used =
        get(ROUTE_PROFILES_PATH)
            .getBody()
            .valueStream()
            .filter(profile -> profile.get("name").asString().equals("used-compress"))
            .findFirst()
            .orElseThrow();
    assertThat(used.get("services").toString())
        .isEqualTo(JSON.writeValueAsString(List.of(api, web).stream().sorted().toList()));
  }

  @Test
  void setIsSavedWholeAndKeepsTheProfilesItDoesNotName() throws Exception {
    putRouteProfiles(json("{\"kept\": {\"compress\": {}}}"));

    ResponseEntity<JsonNode> saved =
        put(
            ROUTE_PROFILES_PATH,
            json(
                """
                {"office-only": {"ipAllowList": {"sourceRange": ["192.168.1.0/24"]}},
                 "behind-https": {"headers": {"customRequestHeaders": {"X-Forwarded-Proto": "https"}}},
                 "protected": {"chain": {"middlewares": ["office-only", "behind-https"]}}}
                """));

    assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(saved.getBody().valueStream().map(profile -> profile.get("name").asString()))
        .containsExactly("behind-https", "kept", "office-only", "protected");
    assertThat(JSON.readTree(kv("protected")))
        .isEqualTo(
            json(
                """
                {"chain": {"middlewares": ["office-only", "behind-https"]}}
                """));
  }

  @Test
  void setWithOneRefusedProfileWritesNothing() throws Exception {
    ResponseEntity<JsonNode> refused =
        put(
            ROUTE_PROFILES_PATH,
            json(
                """
                {"fine": {"compress": {}},
                 "from-file": {"basicAuth": {"usersFile": "/etc/traefik/users"}}}
                """));

    assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(refused.getBody().get("detail").asString()).contains("from-file", "usersFile");
    assertThat(refused.getBody().get("problems").get(0).get("profile").asString())
        .isEqualTo("from-file");
    assertThat(names()).isEmpty();
  }

  @Test
  void refusedReplacementKeepsTheProfileAsItWas() throws Exception {
    putRouteProfiles(
        json("{\"good\": {\"headers\": {\"customRequestHeaders\": {\"X-Old\": \"1\"}}}}"));

    ResponseEntity<JsonNode> refused =
        put(ROUTE_PROFILES_PATH + "/good", json("{\"rewriteEverything\": {}}"));

    assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(refused.getBody().get("detail").asString()).contains("rewriteEverything");
    assertThat(kv("good")).contains("X-Old");
  }

  @Test
  void eachProfileThatCannotWorkOnAnotherWorkstationIsRefusedWithItsReason() throws Exception {
    String[][] refusals = {
      {"from-file", "{\"basicAuth\": {\"usersFile\": \"/etc/traefik/users\"}}", "usersFile"},
      {"digest-from-file", "{\"digestAuth\": {\"usersFile\": \"/etc/users\"}}", "usersFile"},
      {"on-loopback", "{\"forwardAuth\": {\"address\": \"http://127.0.0.1:4181\"}}", "127.0.0.1"},
      {"on-localhost", "{\"forwardAuth\": {\"address\": \"http://localhost:4181\"}}", "localhost"},
      {"on-docker", "{\"forwardAuth\": {\"address\": \"http://172.17.0.5:4181\"}}", "172.17.0.5"},
      {"with-plugin", "{\"plugin\": {\"my-plugin\": {\"option\": 1}}}", "plugin"},
      {"outside-chain", "{\"chain\": {\"middlewares\": [\"other@file\"]}}", "other@file"},
      {"two-types", "{\"headers\": {}, \"compress\": {}}", "one middleware type"},
      {"limited", "{\"rateLimit\": {\"average\": 100}}", "rateLimit"},
      {"bad@name", "{\"compress\": {}}", "not a valid name"},
      {"to-nowhere", "{\"chain\": {\"middlewares\": [\"missing\"]}}", "missing"},
    };
    for (String[] refusal : refusals) {
      ResponseEntity<JsonNode> answer =
          put(ROUTE_PROFILES_PATH + "/" + refusal[0], json(refusal[1]));

      assertThat(answer.getStatusCode()).as(refusal[0]).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(answer.getBody().get("detail").asString()).as(refusal[0]).contains(refusal[2]);
    }
    assertThat(names()).isEmpty();
    assertThat(
            put(
                    ROUTE_PROFILES_PATH + "/on-lan",
                    json("{\"forwardAuth\": {\"address\": \"http://192.168.1.20:4181\"}}"))
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
  }

  @Test
  void chainNamesAStoredProfileAndAChangeToItWritesOnlyThatProfile() throws Exception {
    putRouteProfiles(
        json("{\"stored\": {\"headers\": {\"customRequestHeaders\": {\"X-V\": \"1\"}}}}"));
    putRouteProfiles(json("{\"protected\": {\"chain\": {\"middlewares\": [\"stored\"]}}}"));
    long chainWritten = modifyIndex("protected");

    putRouteProfiles(
        json("{\"stored\": {\"headers\": {\"customRequestHeaders\": {\"X-V\": \"2\"}}}}"));

    assertThat(modifyIndex("protected")).isEqualTo(chainWritten);
    assertThat(JSON.readTree(kv("protected")))
        .isEqualTo(json("{\"chain\": {\"middlewares\": [\"stored\"]}}"));
    assertThat(kv("stored")).contains("\"2\"");
  }

  @Test
  void brokenStoredChainDoesNotBlockASaveThatDoesNotNameIt() throws Exception {
    consulContainer.execInContainer(
        "consul",
        "kv",
        "put",
        "lvh-yard/route-profiles/outside",
        "{\"chain\": {\"middlewares\": [\"other@file\"]}}");

    ResponseEntity<JsonNode> saved =
        put(ROUTE_PROFILES_PATH, json("{\"fresh\": {\"compress\": {}}}"));

    assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(names()).containsExactly("fresh", "outside");
  }

  @Test
  void saveThatNeedsMoreWritesThanOneConsulTransactionIsRefused() throws Exception {
    putRouteProfiles(json("{\"kept\": {\"compress\": {}}}"));
    Map<String, Object> set = new LinkedHashMap<>();
    for (int i = 1; i <= 65; i++) {
      set.put("many-" + i, Map.of("compress", Map.of()));
    }

    ResponseEntity<JsonNode> refused = put(ROUTE_PROFILES_PATH, set);

    assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(refused.getBody().get("detail").asString()).contains("65", "64");
    assertThat(names()).containsExactly("kept");
  }

  @Test
  void chainsThatNameAChangedProfileDoNotCountAsWrites() throws Exception {
    putRouteProfiles(
        json(
            """
            {"base": {"headers": {"customRequestHeaders": {"X-V": "1"}}},
             "protected": {"chain": {"middlewares": ["base"]}}}
            """));
    Map<String, Object> set = new LinkedHashMap<>();
    set.put("base", Map.of("headers", Map.of("customRequestHeaders", Map.of("X-V", "2"))));
    for (int i = 1; i < 64; i++) {
      set.put("many-" + i, Map.of("compress", Map.of()));
    }

    ResponseEntity<JsonNode> saved = put(ROUTE_PROFILES_PATH, set);

    assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(kv("base")).contains("\"2\"");
  }

  @Test
  void profileThatNothingNamesIsRemoved() throws Exception {
    putRouteProfiles(json("{\"old\": {\"compress\": {}}, \"kept\": {\"compress\": {}}}"));

    ResponseEntity<JsonNode> removed = delete(ROUTE_PROFILES_PATH + "/old");

    assertThat(removed.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(names()).containsExactly("kept");
    assertThat(delete(ROUTE_PROFILES_PATH + "/old").getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void profileThatAPublicationOrAChainNamesIsNotRemoved() throws Exception {
    putRouteProfiles(
        json(
            """
            {"login": {"basicAuth": {"users": ["ana:$apr1$H6uskkkW$IgXLP6ewTrSuBkTrqE8wj/"]}},
             "protected": {"chain": {"middlewares": ["login"]}}}
            """));
    String web = newServiceName("web");
    register(web, Map.of("host", "10.1.2.3", "port", 8080));
    put(publicationPath(web), Map.of("profiles", List.of("login")));

    ResponseEntity<JsonNode> refused = delete(ROUTE_PROFILES_PATH + "/login");

    assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(refused.getBody().get("detail").asString()).contains(web, "protected");
    assertThat(refused.getBody().get("services").toString()).isEqualTo("[\"" + web + "\"]");
    assertThat(refused.getBody().get("chains").toString()).isEqualTo("[\"protected\"]");
    assertThat(names()).containsExactly("login", "protected");
  }

  static JsonNode json(String text) {
    return JSON.readTree(text);
  }

  /** The names that reached Consul. */
  private List<String> names() throws Exception {
    ExecResult result =
        consulContainer.execInContainer("consul", "kv", "get", "-keys", "lvh-yard/route-profiles/");
    return result
        .getStdout()
        .lines()
        .map(key -> key.substring("lvh-yard/route-profiles/".length()))
        .toList();
  }

  /** Consul's index of the last write to the profile's key. */
  private long modifyIndex(String profile) throws Exception {
    ExecResult result =
        consulContainer.execInContainer(
            "consul", "kv", "get", "-detailed", "lvh-yard/route-profiles/" + profile);
    return result
        .getStdout()
        .lines()
        .filter(line -> line.startsWith("ModifyIndex"))
        .map(line -> Long.parseLong(line.substring("ModifyIndex".length()).strip()))
        .findFirst()
        .orElseThrow();
  }

  /** The profile's value in the test Workstation's Consul KV, or {@code null} when it has none. */
  private String kv(String profile) throws Exception {
    ExecResult result =
        consulContainer.execInContainer(
            "consul", "kv", "get", "lvh-yard/route-profiles/" + profile);
    return result.getExitCode() == 0 ? result.getStdout().strip() : null;
  }
}
