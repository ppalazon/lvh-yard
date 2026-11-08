package dev.palazon.lvhyard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** A Service's detail page, where its Instances are seen and removed. */
class ServicePageIT extends DashboardIT {

  private static String instanceRow(String instances, String value) {
    int at = instances.indexOf(value, instances.indexOf("<tbody>"));
    assertThat(at).as("Instance row containing %s", value).isNotNegative();
    return instances.substring(instances.lastIndexOf("<tr>", at), instances.indexOf("</tr>", at));
  }

  /** The risks box, up to its own closing tag. */
  private static String risks(String page) {
    int start = page.lastIndexOf("<details", page.indexOf("id=\"risks\""));
    if (start < 0) {
      throw new AssertionError("No risks box");
    }
    return page.substring(start, page.indexOf("</details>", start) + "</details>".length());
  }

  @Test
  void remoteServiceShowsTheSameCardsAndAlertsWithoutChangeButtons() {
    String api = newServiceName("api");
    remote.register(api, Map.of("host", "10.9.8.7", "port", 9090));
    remote.publish(api, "yard");

    String body = getPage("/services/" + RemoteWorkstation.NAME + "/" + api).getBody();

    assertThat(summaryCard(body, "state")).contains("Unhealthy");
    assertThat(summaryCard(body, "publication")).contains("Yard Scope");
    assertThat(summaryCard(body, "passing")).contains("0 of 1");
    assertThat(summaryCard(body, "routing")).isNotEmpty();
    assertThat(div(body, "role=\"alert\"")).contains("No Instance passes its checks");
    assertThat(div(body, "id=\"sections\"")).contains(">Publication</h2>");
    assertThat(div(body, "id=\"service\""))
        .as("only the Reachability test, outside the region, has a button")
        .doesNotContain("<form")
        .doesNotContain("<button");
  }

  @Test
  void remoteServiceWithYardScopeShowsItsHostnameHere() {
    String api = newServiceName("api");
    remote.register(api, Map.of("host", "10.9.8.7", "port", 9090));
    remote.publish(api, "yard");

    String body = getPage("/services/" + RemoteWorkstation.NAME + "/" + api).getBody();

    assertThat(body)
        .contains("Yard Scope")
        .contains("href=\"http://" + api + "." + RemoteWorkstation.NAME + ".lvh.test\"");
  }

  @Test
  void remoteServicesContainerBindingsAreShownWithoutAForm() {
    String web = newServiceName("web");
    String name = newContainerName();
    remote.bind(web, name, 80);

    String page = getPage("/services/" + RemoteWorkstation.NAME + "/" + web).getBody();

    assertThat(page).contains(name).doesNotContain("/container-bindings/");
  }
}
