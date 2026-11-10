package dev.palazon.lvhyard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** The Aliases of a Service, which its Service page adds, shows and removes. */
class AliasesOnTheServicePageComponentTest extends DashboardComponentTest {

  private static String page(String service) {
    return "/services/" + WORKSTATION + "/" + service;
  }

  private String sharedRemoteService(String prefix) {
    String service = newServiceName(prefix);
    remote.register(service, Map.of("host", "10.9.8.7", "port", 3000));
    remote.publish(service, "yard");
    return service;
  }

  private static String target(String remoteService) {
    return RemoteWorkstation.NAME + "/" + remoteService;
  }

  @Test
  void addAliasLinkInTheInstanceSourcesCardOpensTheFormOnItsOwnPage() {
    String web = newServiceName("web");
    create(web);

    assertThat(div(getPage(page(web)).getBody(), "id=\"add-sources\""))
        .contains("href=\"" + page(web) + "/aliases/new\"");
    assertFormPage(page(web), page(web) + "/aliases/new", "Add Alias", page(web) + "/aliases");
  }

  @Test
  void formListsOnlyTheRemoteServicesWithYardScope() {
    String shared = sharedRemoteService("grafana");
    String local = newServiceName("grafana");
    remote.register(local, Map.of("host", "10.9.8.7", "port", 3001));
    remote.publish(local, "local");
    String web = newServiceName("web");
    create(web);

    String form = formTo(getPage(page(web) + "/aliases/new").getBody(), page(web) + "/aliases");

    assertThat(form)
        .contains("name=\"target\"")
        .contains("value=\"" + target(shared) + "\"")
        .doesNotContain("value=\"" + target(local) + "\"")
        .doesNotContain("disabled");
  }

  @Test
  void addedAliasShowsAsASourceWithTheInstancesOfItsRemoteService() {
    String grafana = sharedRemoteService("grafana");
    String web = newServiceName("web");
    create(web);

    ResponseEntity<String> response =
        postForm(page(web) + "/aliases", Map.of("target", target(grafana)));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER);
    assertThat(response.getHeaders().getLocation()).hasPath(page(web));
    String sources = div(getPage(page(web)).getBody(), "id=\"sources\"");
    assertThat(sources)
        .contains("data-source=\"" + target(grafana) + "\"")
        .contains(">Alias</span>")
        .contains(">" + grafana + " on " + RemoteWorkstation.NAME + "</span>")
        .contains("href=\"/services/" + target(grafana) + "\"")
        .contains("10.9.8.7:3000")
        .doesNotContain("data-alias-reason");
    assertThat(formTo(getPage(page(web) + "/aliases/new").getBody(), page(web) + "/aliases"))
        .as("a Remote Service that the Service already has an Alias to")
        .doesNotContain("value=\"" + target(grafana) + "\"");
  }

  @Test
  void refusedAliasShowsTheFormAgainWithTheReasonAtItsField() {
    String grafana = sharedRemoteService("grafana");
    String web = newServiceName("web");
    create(web);
    postForm(page(web) + "/aliases", Map.of("target", target(grafana)));

    ResponseEntity<String> again =
        postForm(page(web) + "/aliases", Map.of("target", target(grafana)));
    ResponseEntity<String> own =
        postForm(page(web) + "/aliases", Map.of("target", WORKSTATION + "/" + web));

    assertThat(again.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(again.getBody())
        .contains("<h1>Add Alias</h1>")
        .contains("id=\"target-error\"")
        .contains("already has an Alias");
    assertThat(own.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(own.getBody()).contains("id=\"target-error\"").contains("this Workstation");
  }

  @Test
  void aliasThatGivesNoInstancesSaysWhy() {
    String grafana = sharedRemoteService("grafana");
    String web = newServiceName("web");
    create(web);
    postForm(page(web) + "/aliases", Map.of("target", target(grafana)));

    remote.publish(grafana, "local");

    String sources = div(getPage(page(web)).getBody(), "id=\"sources\"");
    assertThat(sources).contains("data-alias-reason").contains("Local Scope");
  }

  @Test
  void removeButtonRemovesTheAlias() {
    String grafana = sharedRemoteService("grafana");
    String web = newServiceName("web");
    create(web);
    postForm(page(web) + "/aliases", Map.of("target", target(grafana)));
    String remove = page(web) + "/aliases/" + target(grafana) + "/remove";
    assertThat(getPage(page(web)).getBody()).contains("action=\"" + remove + "\"");

    ResponseEntity<String> response = postForm(remove, Map.of());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER);
    assertThat(getPage(page(web)).getBody()).doesNotContain("data-source=\"" + target(grafana));
  }
}
