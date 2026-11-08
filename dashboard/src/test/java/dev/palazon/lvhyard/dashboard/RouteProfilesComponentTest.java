package dev.palazon.lvhyard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The test Workstation's Route Profiles, as the API adds, lists and removes them, and as its Consul
 * KV holds them for the other Workstations.
 */
class RouteProfilesComponentTest extends DashboardComponentTest {

  private static final JsonMapper JSON = new JsonMapper();

  @AfterEach
  void removeTheProfiles() throws Exception {
    removeAllRouteProfiles();
  }

  static JsonNode json(String text) {
    return JSON.readTree(text);
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
}
