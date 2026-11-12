package cc.lvhy.yard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** The Catalog page, the dashboard's home. */
class CatalogPageComponentTest extends DashboardComponentTest {

  /** The table row that links to {@code service}'s page. */
  private static String rowOf(String page, String service) {
    int link = page.indexOf("href=\"/services/ws-test/" + service + "\"");
    assertThat(link).as("a row for %s", service).isNotNegative();
    return page.substring(page.lastIndexOf("<tr", link), page.indexOf("</tr>", link));
  }

  @Test
  void namesTheWorkstation() {
    ResponseEntity<String> response = getPage("/");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).contains("LVH Yard · ws-test").contains("base domain lvh.test");
  }

  @Test
  void leavesTheOtherListsToTheNavigationBar() {
    String page = getPage("/").getBody();
    String main = page.substring(page.indexOf("<main"));

    assertThat(main).doesNotContain("href=\"/containers\"").doesNotContain("href=\"/aliases\"");
  }

  @Test
  void listsOneRowPerServiceWithItsPassingCount() {
    String grafana = newServiceName("grafana");
    int port = newLanPort();
    listenOnLanAddress(port);
    register(grafana, Map.of("port", port, "check", Map.of("interval", "1s")));
    register(grafana, Map.of("host", "10.1.2.3", "port", 3001));

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> {
          String page = getPage("/").getBody();
          assertThat(page)
              .containsOnlyOnce("href=\"/services/ws-test/" + grafana + "\"")
              .contains("<th>Service</th><th>State</th><th>Hostname</th><th>Instances</th>")
              .doesNotContain("<th>Instance</th>", "<th>Health</th>")
              .doesNotContain("10.1.2.3:3001");
          assertThat(rowOf(page, grafana)).contains("Unpublished", "1 of 2 passing");
        });
  }

  @Test
  void showsEachServicesState() {
    String web = newServiceName("web");
    String api = newServiceName("api");
    register(web, Map.of("host", "10.1.2.3", "port", 8080));
    register(api, Map.of("host", "10.1.2.3", "port", 9090));
    publish(api);

    String page = getPage("/").getBody();

    assertThat(rowOf(page, web)).contains("Unpublished");
    assertThat(rowOf(page, api)).contains("Unhealthy").contains(api + ".lvh.test");
  }

  @Test
  void serviceHostnameOpensInANewTabAndTheServicePageDoesNot() {
    String api = newServiceName("api");
    register(api, Map.of("host", "10.1.2.3", "port", 9090));
    publish(api);

    String page = getPage("/").getBody();

    assertThat(openingTag(page, "href=\"http://" + api + ".lvh.test"))
        .contains("target=\"_blank\"")
        .contains("rel=\"noopener noreferrer\"");
    assertThat(openingTag(page, "href=\"/services/ws-test/" + api + "\""))
        .doesNotContain("target=")
        .doesNotContain("rel=");
  }

  @Test
  void showsGoodStatesWithGreenBadges() {
    String web = newServiceName("web");
    int port = newLanPort();
    listenOnLanAddress(port);
    register(web, Map.of("port", port, "check", Map.of("interval", "1s")));
    publish(web);

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() ->
            assertThat(rowOf(getPage("/").getBody(), web))
                .contains(badge("success", "Healthy"))
                .contains("1 of 1 passing"));
  }

  @Test
  void showsBadStatesWithTheirOwnBadges() {
    String web = newServiceName("web");
    register(web, Map.of("host", "10.1.2.3", "port", 8080));
    publish(web);

    assertThat(rowOf(getPage("/").getBody(), web))
        .contains(badge("destructive", "Unhealthy"))
        .contains("0 of 1 passing");
  }

  @Test
  void listsUnavailableServiceWithoutInstances() {
    String web = newServiceName("web");
    String id = register(web, Map.of("host", "10.1.2.3", "port", 8080)).get("id").asString();
    publish(web);
    delete(instancesPath(web) + "/" + id);

    assertThat(rowOf(getPage("/").getBody(), web))
        .contains("Unavailable", badge("outline", "Empty"));
  }

  @Test
  void filtersByState() {
    String web = newServiceName("web");
    String api = newServiceName("api");
    register(web, Map.of("host", "10.1.2.3", "port", 8080));
    register(api, Map.of("host", "10.1.2.3", "port", 9090));
    publish(api);

    String page = getPage("/?state=unhealthy").getBody();

    assertThat(page)
        .contains("href=\"/services/ws-test/" + api + "\"")
        .doesNotContain("href=\"/services/ws-test/" + web + "\"");
  }

  @Test
  void saysWhenTheYardHasNoSuchWorkstation() {
    assertThat(getPage("/?workstation=ws-typo").getBody())
        .contains("The Yard has no Workstation ws-typo.");
  }

  @Test
  void filterFormKeepsTheFiltersInThePageAddress() {
    String page = getPage("/?workstation=" + RemoteWorkstation.NAME + "&state=healthy").getBody();

    String form = page.substring(page.indexOf("<form"), page.indexOf("</form>"));
    assertThat(form)
        .contains("name=\"workstation\"")
        .contains("name=\"state\"")
        .contains("<option value=\"ws-test\">ws-test</option>")
        .contains("<option value=\"" + RemoteWorkstation.NAME + "\" selected=\"selected\">")
        .contains("<option value=\"healthy\" selected=\"selected\">Healthy</option>")
        .contains("<option value=\"unpublished\">Unpublished</option>");
  }

  @Test
  void filterFormLoadsTheServicesOnEachChangeWithoutAButton() {
    String page = getPage("/").getBody();

    assertThat(openingTag(page, "<form"))
        .contains("hx-get=\"/\"")
        .contains("hx-trigger=\"change\"")
        .contains("hx-target=\"#catalog\"")
        .contains("hx-select=\"#catalog\"")
        .contains("hx-swap=\"outerHTML\"")
        .contains("hx-push-url=\"true\"");
    assertThat(page.substring(page.indexOf("<form"), page.indexOf("</form>")))
        .doesNotContain("<button");
  }

  @Test
  void refreshesItsServicesWithYardChangedSignalAndFallbackPoll() {
    String page = getPage("/?workstation=" + RemoteWorkstation.NAME + "&state=healthy").getBody();

    String region = openingTag(page, "id=\"catalog\"");
    assertThat(region)
        .contains("hx-get=\"/?workstation=" + RemoteWorkstation.NAME + "&amp;state=healthy\"")
        .contains("hx-trigger=\"yard-changed from:body, every 30s\"")
        .contains("hx-select=\"#catalog\"")
        .contains("hx-swap=\"outerHTML\"");
    assertThat(div(page, "id=\"catalog\""))
        .as("the region a refresh swaps, which leaves the filter form")
        .doesNotContain("<form");
  }

  @Test
  void refreshesFromThePlainCatalogWithoutFilters() {
    assertThat(openingTag(getPage("/").getBody(), "id=\"catalog\"")).contains("hx-get=\"/\"");
  }

  @Test
  void groupsServicesByHomeWorkstationThisOneFirst() {
    String web = newServiceName("web");
    register(web, Map.of("host", "10.1.2.3", "port", 8080));
    String api = newServiceName("api");
    remote.register(api, Map.of("host", "10.9.8.7", "port", 9090));

    String page = getPage("/").getBody();

    int own = page.indexOf("<h2>ws-test</h2>");
    int other = page.indexOf("<h2>" + RemoteWorkstation.NAME + "</h2>");
    assertThat(own).as("this Workstation's heading").isNotNegative();
    assertThat(other).as("the remote Workstation's heading").isGreaterThan(own);
    assertThat(page.indexOf("href=\"/services/ws-test/" + web + "\"")).isBetween(own, other);
    assertThat(page.indexOf("href=\"/services/" + RemoteWorkstation.NAME + "/" + api + "\""))
        .isGreaterThan(other);
    assertThat(page).doesNotContain("10.9.8.7:9090");
  }

  @Test
  void filtersByWorkstation() {
    String web = newServiceName("web");
    register(web, Map.of("host", "10.1.2.3", "port", 8080));
    String api = newServiceName("api");
    remote.register(api, Map.of("host", "10.9.8.7", "port", 9090));

    String page = getPage("/?workstation=" + RemoteWorkstation.NAME).getBody();

    assertThat(page)
        .contains("<h2>" + RemoteWorkstation.NAME + "</h2>")
        .contains("href=\"/services/" + RemoteWorkstation.NAME + "/" + api + "\"")
        .doesNotContain("<h2>ws-test</h2>")
        .doesNotContain("href=\"/services/ws-test/" + web + "\"");
  }
}
