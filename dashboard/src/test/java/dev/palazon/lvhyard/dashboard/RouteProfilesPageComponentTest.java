package dev.palazon.lvhyard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.HtmlUtils;
import tools.jackson.databind.json.JsonMapper;

/** The Route Profiles page, where this Workstation's Route Profiles are seen, added and removed. */
class RouteProfilesPageComponentTest extends DashboardComponentTest {

  private static final JsonMapper JSON = new JsonMapper();

  @AfterEach
  void removeTheProfiles() throws Exception {
    removeAllRouteProfiles();
  }

  @Test
  void listsEachProfileWithItsTypeAndTheServicesThatUseIt() {
    putRouteProfiles(
        JSON.readTree(
            """
            {"page-login": {"basicAuth": {"users": ["ana:$apr1$H6uskkkW$IgXLP6ewTrSuBkTrqE8wj/"]}},
             "page-https": {"headers": {"customRequestHeaders": {"X-Forwarded-Proto": "https"}}}}
            """));
    String web = newServiceName("web");
    register(web, Map.of("host", "10.1.2.3", "port", 8080));
    put(publicationPath(web), Map.of("profiles", List.of("page-login")));

    String page = getPage("/route-profiles").getBody();

    assertThat(row(page, "page-login")).contains("basicAuth").contains(web);
    assertThat(row(page, "page-https")).contains("headers").contains("Not used");
  }

  @Test
  void withNoProfilesSaysSo() {
    assertThat(getPage("/route-profiles").getBody()).contains("No Route Profiles yet.");
  }

  @Test
  void savingTheFormAddsTheProfile() {
    ResponseEntity<String> response =
        postForm(
            "/route-profiles",
            Map.of(
                "name",
                "page-office",
                "definition",
                "ipAllowList:\n  sourceRange:\n    - 192.168.1.0/24\n"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER);
    assertThat(response.getHeaders().getLocation()).hasPath("/route-profiles");
    assertThat(row(getPage("/route-profiles").getBody(), "page-office")).contains("ipAllowList");
  }

  @Test
  void refusedSaveShowsTheReasonAndKeepsTheText() {
    String text = "basicAuth:\n  usersFile: /etc/users\n";

    ResponseEntity<String> response =
        postForm("/route-profiles", Map.of("name", "page-file", "definition", text));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody())
        .contains("page-file reads its users from usersFile")
        .contains("value=\"page-file\"")
        .contains(">" + text + "</textarea>");
    assertThat(getPage("/route-profiles").getBody()).doesNotContain("id=\"profile-page-file\"");
  }

  @Test
  void yamlThatDoesNotParseIsRefusedWithItsPlace() {
    String text = "headers:\n  customRequestHeaders: [unclosed\n";

    ResponseEntity<String> response =
        postForm("/route-profiles", Map.of("name", "page-broken", "definition", text));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).contains("not valid YAML").contains("line 3");
  }

  @Test
  void profileWithNoNameIsRefused() {
    ResponseEntity<String> response =
        postForm("/route-profiles", Map.of("name", " ", "definition", "compress: {}\n"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).contains("needs a name");
  }

  @Test
  void changingAProfileFromItsRowFillsTheFormWithItsYaml() {
    putRouteProfiles(
        JSON.readTree(
            """
            {"page-change": {"headers": {"customRequestHeaders": {"X-Forwarded-Proto": "https"}}}}
            """));
    assertThat(row(getPage("/route-profiles").getBody(), "page-change"))
        .contains("href=\"/route-profiles/page-change/edit\"");

    String page = getPage("/route-profiles/page-change/edit").getBody();

    assertThat(valueOf(openingTag(page, "id=\"name\""))).isEqualTo("page-change");
    assertThat(textBoxOf(page))
        .isEqualTo("headers:\n  customRequestHeaders:\n    X-Forwarded-Proto: https\n");

    postForm("/route-profiles", Map.of("name", "page-change", "definition", "compress: {}\n"));

    assertThat(row(getPage("/route-profiles").getBody(), "page-change")).contains("compress");
  }

  @Test
  void changingAMissingProfileIsNotFound() {
    assertThat(getPage("/route-profiles/page-missing/edit").getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void removingAProfileTakesItOffThePage() {
    putRouteProfiles(JSON.readTree("{\"page-gone\": {\"compress\": {}}}"));
    assertThat(row(getPage("/route-profiles").getBody(), "page-gone"))
        .contains("action=\"/route-profiles/page-gone/remove\"");

    ResponseEntity<String> response = postForm("/route-profiles/page-gone/remove", Map.of());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER);
    assertThat(response.getHeaders().getLocation()).hasPath("/route-profiles");
    assertThat(getPage("/route-profiles").getBody()).doesNotContain("id=\"profile-page-gone\"");
  }

  @Test
  void refusedRemoveShowsTheServicesAndChainsThatUseIt() {
    putRouteProfiles(
        JSON.readTree(
            """
            {"page-used": {"compress": {}},
             "page-chain": {"chain": {"middlewares": ["page-used"]}}}
            """));
    String web = newServiceName("web");
    register(web, Map.of("host", "10.1.2.3", "port", 8080));
    put(publicationPath(web), Map.of("profiles", List.of("page-used")));

    ResponseEntity<String> response = postForm("/route-profiles/page-used/remove", Map.of());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody())
        .contains("page-used is in use")
        .contains("The Publications of these Services name it: " + web + ".")
        .contains("These chains name it: page-chain.");
    assertThat(getPage("/route-profiles").getBody()).contains("id=\"profile-page-used\"");
  }

  @Test
  void servicePageLinksEachNamedProfileToItsRow() {
    putRouteProfiles(JSON.readTree("{\"page-linked\": {\"compress\": {}}}"));
    String web = newServiceName("web");
    register(web, Map.of("host", "10.1.2.3", "port", 8080));
    put(publicationPath(web), Map.of("profiles", List.of("page-linked")));

    assertThat(getPage("/services/ws-test/" + web).getBody())
        .contains("href=\"/route-profiles#profile-page-linked\"");
  }

  @Test
  void unpublishedServicePageOffersTheProfilesAndAddingOnePublishesTheService() {
    putRouteProfiles(JSON.readTree("{\"page-offered\": {\"compress\": {}}}"));
    String web = newServiceName("web");
    create(web);

    assertThat(getPage("/services/ws-test/" + web).getBody())
        .contains("id=\"route-profiles\"")
        .contains("aria-label=\"Add page-offered\"");

    postForm("/services/ws-test/" + web + "/publish", Map.of("profiles", "page-offered"));

    assertThat(get(servicePath(web)).getBody().get("profiles").get(0).asString())
        .isEqualTo("page-offered");
  }

  private static String valueOf(String tag) {
    int start = tag.indexOf("value=\"") + "value=\"".length();
    return HtmlUtils.htmlUnescape(tag.substring(start, tag.indexOf('"', start)));
  }

  private static String textBoxOf(String page) {
    int start = page.indexOf(">", page.indexOf("<textarea")) + 1;
    return HtmlUtils.htmlUnescape(page.substring(start, page.indexOf("</textarea>", start)));
  }

  /** The row of {@code profile}, which other pages link to by its id. */
  private static String row(String page, String profile) {
    int start = page.lastIndexOf("<tr", page.indexOf("id=\"profile-" + profile + "\""));
    assertThat(start).as("the row of " + profile).isNotNegative();
    return page.substring(start, page.indexOf("</tr>", start));
  }
}
