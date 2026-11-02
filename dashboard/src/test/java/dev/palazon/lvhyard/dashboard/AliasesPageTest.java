package dev.palazon.lvhyard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** The Aliases page, where this Workstation's Aliases are added, seen and removed. */
class AliasesPageTest extends DashboardTest {

  @Test
  void catalogLinksToTheAliasesPage() {
    assertThat(getPage("/").getBody()).contains("href=\"/aliases\"");
  }

  @Test
  void formOffersEveryRemoteServiceWithYardScope() {
    String shared = newServiceName("grafana");
    remote.register(shared, Map.of("host", "10.9.8.7", "port", 3000));
    remote.publish(shared, "yard");
    String local = newServiceName("grafana");
    remote.register(local, Map.of("host", "10.9.8.7", "port", 3001));
    remote.publish(local, "local");

    String page = getPage("/aliases").getBody();

    assertThat(page)
        .contains("name=\"name\"")
        .contains("value=\"" + RemoteWorkstation.NAME + "/" + shared + "\"")
        .doesNotContain("value=\"" + RemoteWorkstation.NAME + "/" + local + "\"");
  }

  @Test
  void addingAnAliasShowsItWithItsHostname() {
    String grafana = newServiceName("grafana");
    remote.register(grafana, Map.of("host", "10.9.8.7", "port", 3000));
    remote.publish(grafana, "yard");

    ResponseEntity<String> response =
        postForm(
            "/aliases", Map.of("name", grafana, "target", RemoteWorkstation.NAME + "/" + grafana));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER);
    assertThat(response.getHeaders().getLocation()).hasPath("/aliases");
    assertThat(getPage("/aliases").getBody())
        .contains("href=\"http://" + grafana + ".lvh.test\"")
        .contains("href=\"/services/" + RemoteWorkstation.NAME + "/" + grafana + "\"")
        .contains("Active");
  }

  @Test
  void invalidNameIsShownInTheFormWithWhatWasTyped() {
    String grafana = newServiceName("grafana");
    remote.register(grafana, Map.of("host", "10.9.8.7", "port", 3000));
    remote.publish(grafana, "yard");

    ResponseEntity<String> response =
        postForm(
            "/aliases", Map.of("name", "yard", "target", RemoteWorkstation.NAME + "/" + grafana));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody())
        .contains("taken by the dashboard")
        .contains("aria-invalid=\"true\"")
        .contains("value=\"yard\"");
  }

  @Test
  void aliasWithoutARouteShowsWhy() {
    String grafana = newServiceName("grafana");
    remote.register(grafana, Map.of("host", "10.9.8.7", "port", 3000));
    remote.publish(grafana, "yard");
    postForm("/aliases", Map.of("name", grafana, "target", RemoteWorkstation.NAME + "/" + grafana));

    remote.publish(grafana, "local");

    assertThat(getPage("/aliases").getBody()).contains("Not shared").contains("Local Scope");
  }

  @Test
  void removingAnAliasTakesItOffThePage() {
    String grafana = newServiceName("grafana");
    remote.register(grafana, Map.of("host", "10.9.8.7", "port", 3000));
    remote.publish(grafana, "yard");
    postForm("/aliases", Map.of("name", grafana, "target", RemoteWorkstation.NAME + "/" + grafana));
    assertThat(getPage("/aliases").getBody())
        .contains("action=\"/aliases/" + grafana + "/remove\"");

    ResponseEntity<String> response = postForm("/aliases/" + grafana + "/remove", Map.of());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER);
    assertThat(response.getHeaders().getLocation()).hasPath("/aliases");
    assertThat(getPage("/aliases").getBody()).doesNotContain("http://" + grafana + ".lvh.test");
  }
}
