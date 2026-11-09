package dev.palazon.lvhyard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/** The Alias button on a Remote Service row of the catalog, and the form it opens. */
class AliasButtonInTheCatalogComponentTest extends DashboardComponentTest {

  private static String remotePage(String remoteService) {
    return "/services/" + RemoteWorkstation.NAME + "/" + remoteService;
  }

  private static String formPath(String remoteService) {
    return remotePage(remoteService) + "/alias/new";
  }

  private static String action(String remoteService) {
    return remotePage(remoteService) + "/alias";
  }

  /** The catalog row of the Service whose page is {@code page}. */
  private static String rowOf(String catalog, String page) {
    int link = catalog.indexOf("href=\"" + page + "\"");
    assertThat(link).as("a row for %s", page).isNotNegative();
    return catalog.substring(catalog.lastIndexOf("<tr", link), catalog.indexOf("</tr>", link));
  }

  private String remoteService(String prefix, String scope) {
    String service = newServiceName(prefix);
    remote.register(service, Map.of("host", "10.9.8.7", "port", 3000));
    remote.publish(service, scope);
    return service;
  }

  @Test
  void remoteServiceWithYardScopeHasAnAliasButtonAndOneWithLocalScopeHasNone() {
    String shared = remoteService("grafana", "yard");
    String local = remoteService("grafana", "local");
    String own = newServiceName("own");
    register(own, Map.of("host", "10.1.2.3", "port", 8080));
    put(publicationPath(own), Map.of("scope", "yard"));

    String catalog = getPage("/").getBody();

    assertThat(rowOf(catalog, remotePage(shared)))
        .contains("href=\"" + formPath(shared) + "\"")
        .contains(">Alias</a>");
    assertThat(rowOf(catalog, remotePage(local))).doesNotContain("/alias/new");
    assertThat(rowOf(catalog, "/services/" + WORKSTATION + "/" + own)).doesNotContain("/alias/new");
  }

  @Test
  void aliasButtonOpensAFormThatOffersTheServicesOfThisWorkstation() {
    String grafana = remoteService("grafana", "yard");
    String own = newServiceName("own");
    create(own);

    ResponseEntity<String> response = getPage(formPath(grafana));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).contains("<h1>Add Alias</h1>");
    String form = formTo(response.getBody(), action(grafana));
    assertThat(openingTag(form, "action=")).contains("method=\"post\"");
    assertThat(form).contains("href=\"/\"").contains("Cancel</a>");
    assertThat(openingTag(form, "name=\"name\"")).contains("value=\"" + grafana + "\"");
    assertThat(form).contains("<option value=\"" + own + "\"");
  }

  @Test
  void newNameCreatesTheServiceWithLocalScopeAndAddsTheAlias() {
    String grafana = remoteService("grafana", "yard");
    String name = newServiceName("dash");

    ResponseEntity<String> response = postForm(action(grafana), Map.of("name", name));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER);
    assertThat(response.getHeaders().getLocation())
        .hasPath("/services/" + WORKSTATION + "/" + name);
    JsonNode service = get(servicePath(name)).getBody();
    assertThat(service.get("created").asBoolean()).isTrue();
    assertThat(service.get("publication").get("scope").asString()).isEqualTo("local");
    assertThat(InstancePriorityComponentTest.ids(service.get("sources")))
        .containsExactly(RemoteWorkstation.NAME + "/" + grafana);
  }

  @Test
  void existingServiceKeepsItsPublicationAndTheAliasGoesToTheEnd() {
    String grafana = remoteService("grafana", "yard");
    String web = newServiceName("web");
    register(web, Map.of("host", "10.1.2.3", "port", 8080));
    put(publicationPath(web), Map.of("scope", "yard"));

    ResponseEntity<String> response = postForm(action(grafana), Map.of("name", web));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER);
    JsonNode service = get(servicePath(web)).getBody();
    assertThat(service.get("publication").get("scope").asString()).isEqualTo("yard");
    List<String> ids = InstancePriorityComponentTest.ids(service.get("sources"));
    assertThat(ids).hasSize(2);
    assertThat(ids.get(1)).isEqualTo(RemoteWorkstation.NAME + "/" + grafana);
  }

  @Test
  void refusalsShowTheFormAgainWithTheReasonAtTheirField() {
    String grafana = remoteService("grafana", "yard");
    String web = newServiceName("web");
    postForm(action(grafana), Map.of("name", web));

    ResponseEntity<String> badName = postForm(action(grafana), Map.of("name", "Web_1"));
    ResponseEntity<String> again = postForm(action(grafana), Map.of("name", web));
    remote.publish(grafana, "local");
    String other = newServiceName("other");
    ResponseEntity<String> notShared = postForm(action(grafana), Map.of("name", other));

    assertThat(badName.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(badName.getBody())
        .contains("<h1>Add Alias</h1>")
        .contains("id=\"name-error\"")
        .contains("value=\"Web_1\"");
    assertThat(again.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(again.getBody()).contains("id=\"name-error\"").contains("already has an Alias");
    assertThat(notShared.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(notShared.getBody()).contains("id=\"target-error\"").contains("Local Scope");
    assertThat(get(servicePath(other)).getStatusCode())
        .as("a refused Alias creates no Service")
        .isEqualTo(HttpStatus.NOT_FOUND);
  }
}
