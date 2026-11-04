package dev.palazon.lvhyard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** The navigation bar in the header of every page. */
class NavigationBarTest extends DashboardTest {

  @Test
  void linksToEachList() {
    assertThat(navOf(getPage("/").getBody()))
        .contains("href=\"/\"")
        .contains(">Catalog<")
        .contains("href=\"/containers\"")
        .contains(">Containers<")
        .contains("href=\"/aliases\"")
        .contains(">Aliases<")
        .contains("href=\"/static-instances\"")
        .contains(">Static Instances<");
  }

  @Test
  void eachListMarksItsOwnLinkAsCurrent() {
    assertThat(currentLinkOf("/")).isEqualTo("/");
    assertThat(currentLinkOf("/containers")).isEqualTo("/containers");
    assertThat(currentLinkOf("/aliases")).isEqualTo("/aliases");
    assertThat(currentLinkOf("/register")).isEqualTo("/static-instances");
  }

  @Test
  void otherPagesHaveNoCurrentLink() {
    String web = newServiceName("web");
    String id = register(web, Map.of("host", "10.1.2.3", "port", 8080)).get("id").asString();

    assertThat(navOf(getPage("/services/ws-test/" + web).getBody())).doesNotContain("aria-current");
    assertThat(navOf(getPage("/services/ws-test/" + web + "/instances/" + id + "/edit").getBody()))
        .doesNotContain("aria-current");
    assertThat(navOf(getPage("/status").getBody())).doesNotContain("aria-current");
  }

  @Test
  void staticInstancesRedirectsToTheRegisterForm() {
    ResponseEntity<String> response = getPage("/static-instances");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.MOVED_PERMANENTLY);
    assertThat(response.getHeaders().getLocation()).hasPath("/register");
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
