package cc.lvhy.yard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import cc.lvhy.yard.dashboard.consul.Consul.Registration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** The "New Service" form of the catalog, and "Delete Service" on the Service page. */
class CreateAndDeleteServicesPageComponentTest extends DashboardComponentTest {

  private static final String NEW_PATH = "/services/new";

  private static final String CREATE_PATH = "/services";

  /** The table row that links to {@code service}'s page. */
  private static String rowOf(String page, String service) {
    int link = page.indexOf("href=\"/services/ws-test/" + service + "\"");
    assertThat(link).as("a row for %s", service).isNotNegative();
    return page.substring(page.lastIndexOf("<tr", link), page.indexOf("</tr>", link));
  }

  private static String deletePath(String service) {
    return "/services/ws-test/" + service + "/delete";
  }

  @Test
  void catalogOpensTheNewServiceForm() {
    assertFormPage("/", NEW_PATH, "New Service", CREATE_PATH);
  }

  @Test
  void newServiceFormAsksOnlyForAName() {
    String form = formTo(getPage(NEW_PATH).getBody(), CREATE_PATH);

    assertThat(form).contains("name=\"name\"");
    assertThat(form.split("<input", -1)).hasSize(2);
  }

  @Test
  void newServiceFormCreatesTheServiceAndOpensItsPage() {
    String grafana = newServiceName("grafana");

    ResponseEntity<String> response = postForm(CREATE_PATH, Map.of("name", grafana));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER);
    assertThat(response.getHeaders().getLocation()).hasPath("/services/ws-test/" + grafana);
    assertThat(get(servicePath(grafana)).getBody().get("state").asString())
        .isEqualTo("unpublished");
  }

  @Test
  void newServiceFormShowsABadNameAtItsField() {
    ResponseEntity<String> response = postForm(CREATE_PATH, Map.of("name", "My_Grafana"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).contains("<h1>New Service</h1>");
    assertThat(openingTag(response.getBody(), "name=\"name\""))
        .contains("aria-invalid=\"true\"")
        .contains("value=\"My_Grafana\"");
  }

  @Test
  void newServiceFormOpensThePageOfAServiceThatExists() {
    String grafana = newServiceName("grafana");
    create(grafana);

    ResponseEntity<String> response = postForm(CREATE_PATH, Map.of("name", grafana));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER);
    assertThat(response.getHeaders().getLocation()).hasPath("/services/ws-test/" + grafana);
  }

  @Test
  void catalogMarksAnEmptyService() {
    String empty = newServiceName("empty");
    String full = newServiceName("full");
    create(empty);
    register(full, Map.of("host", "10.1.2.3", "port", 3000));

    String page = getPage("/").getBody();

    assertThat(rowOf(page, empty)).contains("Unpublished").contains(badge("outline", "Empty"));
    assertThat(rowOf(page, full)).doesNotContain(badge("outline", "Empty"));
  }

  @Test
  void deleteConfirmationNamesWhatGoes() {
    String web = newServiceName("web");
    register(web, Map.of("host", "10.1.2.3", "port", 3000));
    register(web, Map.of("host", "10.1.2.3", "port", 3001));
    bind(web, newContainerName(), 80);
    publish(web);

    ResponseEntity<String> response = getPage(deletePath(web));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    String page = response.getBody();
    assertThat(page)
        .contains("<h1>Delete Service " + web + "</h1>")
        .contains("its Publication")
        .contains("2 Static Instances")
        .contains("1 Container Binding")
        .contains("Containers keep running");
    String form = formTo(page, deletePath(web));
    assertThat(openingTag(form, "action=\"" + deletePath(web) + "\"")).contains("method=\"post\"");
    assertThat(form).contains("href=\"/services/ws-test/" + web + "\"");
  }

  @Test
  void deleteConfirmationLeavesOutWhatTheServiceDoesNotHave() {
    String web = newServiceName("web");
    create(web);

    assertThat(getPage(deletePath(web)).getBody())
        .contains("the Service itself")
        .doesNotContain("Static Instance")
        .doesNotContain("Container Binding")
        .doesNotContain("its Publication");
  }

  @Test
  void deleteServiceGoesBackToTheCatalog() {
    String web = newServiceName("web");
    create(web);

    ResponseEntity<String> response = postForm(deletePath(web), Map.of());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER);
    assertThat(response.getHeaders().getLocation()).hasPath("/");
    assertThat(get(servicePath(web)).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void deletedServiceWithForeignInstancesLeftSaysWhyItStays() {
    String web = newServiceName("web");
    register(web, Map.of("host", "10.1.2.3", "port", 3000));
    String id = "foreign-" + UUID.randomUUID();
    consul.register(new Registration(id, web, "10.9.8.7", 8080, List.of(), Map.of(), null));
    try {
      ResponseEntity<String> response = postForm(deletePath(web), Map.of());

      assertThat(response.getHeaders().getLocation()).hasPath("/services/ws-test/" + web);
      assertThat(getPage("/services/ws-test/" + web).getBody()).contains("data-foreign-only");
    } finally {
      consul.deregister(id);
    }
  }

  @Test
  void serviceThatOnlyForeignInstancesGiveHasThePartsOfACreatedService() {
    String web = newServiceName("web");
    String id = "foreign-" + UUID.randomUUID();
    consul.register(new Registration(id, web, "10.9.8.7", 8080, List.of(), Map.of(), null));
    try {
      String page = getPage("/services/ws-test/" + web).getBody();

      assertThat(page)
          .contains("id=\"add-sources\"")
          .contains("href=\"/services/ws-test/" + web + "/aliases/new\"")
          .contains("href=\"/services/ws-test/" + web + "/instances/new\"")
          .contains(deletePath(web))
          .doesNotContain("create a Service");
    } finally {
      consul.deregister(id);
    }
  }
}
