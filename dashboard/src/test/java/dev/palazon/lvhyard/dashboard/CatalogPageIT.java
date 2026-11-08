package dev.palazon.lvhyard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** The Catalog page, the dashboard's home. */
class CatalogPageIT extends DashboardIT {

  /** The table row that links to {@code service}'s page. */
  private static String rowOf(String page, String service) {
    int link = page.indexOf("href=\"/services/ws-test/" + service + "\"");
    assertThat(link).as("a row for %s", service).isNotNegative();
    return page.substring(page.lastIndexOf("<tr", link), page.indexOf("</tr>", link));
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
