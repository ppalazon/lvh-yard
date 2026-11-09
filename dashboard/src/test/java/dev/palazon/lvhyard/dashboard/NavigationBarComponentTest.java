package dev.palazon.lvhyard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** The navigation bar in the header of every page. */
class NavigationBarComponentTest extends DashboardComponentTest {

  @Test
  void linksToEachList() {
    assertThat(navOf(getPage("/").getBody()))
        .contains("href=\"/\"")
        .contains(">Catalog<")
        .doesNotContain("href=\"/aliases\"")
        .doesNotContain(">Aliases<")
        .contains("href=\"/route-profiles\"")
        .contains(">Route Profiles<")
        .contains("href=\"/backups\"")
        .contains(">Backups<")
        .contains("href=\"/status\"")
        .contains(">Status<");
  }

  @Test
  void menuHoldsCatalogRouteProfilesBackupsAndStatusInThisOrder() {
    String nav = navOf(getPage("/").getBody());

    assertThat(nav.split("<a ", -1)).hasSize(5);
    assertThat(nav.indexOf(">Catalog<")).isLessThan(nav.indexOf(">Route Profiles<"));
    assertThat(nav.indexOf(">Route Profiles<")).isLessThan(nav.indexOf(">Backups<"));
    assertThat(nav.indexOf(">Backups<")).isLessThan(nav.indexOf(">Status<"));
  }

  @Test
  void eachListMarksItsOwnLinkAsCurrent() {
    assertThat(currentLinkOf("/")).isEqualTo("/");
    assertThat(currentLinkOf("/route-profiles")).isEqualTo("/route-profiles");
    assertThat(currentLinkOf("/backups")).isEqualTo("/backups");
    assertThat(currentLinkOf("/status")).isEqualTo("/status");
  }

  @Test
  void otherPagesHaveNoCurrentLink() {
    String web = newServiceName("web");
    String id = register(web, Map.of("host", "10.1.2.3", "port", 8080)).get("id").asString();

    assertThat(navOf(getPage("/services/ws-test/" + web).getBody())).doesNotContain("aria-current");
    assertThat(navOf(getPage("/services/ws-test/" + web + "/instances/" + id + "/edit").getBody()))
        .doesNotContain("aria-current");
  }

  /** The address of the one link that has {@code aria-current="page"}. */
  private String currentLinkOf(String path) {
    String nav = navOf(getPage(path).getBody());
    int current = nav.indexOf("aria-current=\"page\"");
    assertThat(current).as("the current link of " + path).isNotNegative();
    assertThat(nav.indexOf("aria-current", current + 1)).as("a second current link").isNegative();
    int link = nav.lastIndexOf("<a ", current);
    int href = nav.indexOf("href=\"", link) + "href=\"".length();
    return nav.substring(href, nav.indexOf('"', href));
  }

  private static String navOf(String page) {
    int start = page.indexOf("<nav aria-label=\"Main\"");
    assertThat(start).as("the navigation bar").isNotNegative();
    return page.substring(start, page.indexOf("</nav>", start));
  }
}
