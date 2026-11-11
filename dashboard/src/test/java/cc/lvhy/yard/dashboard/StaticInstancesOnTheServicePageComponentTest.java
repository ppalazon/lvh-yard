package cc.lvhy.yard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/** Static Instances on the Service page: the add form, and edit and remove for each one. */
class StaticInstancesOnTheServicePageComponentTest extends DashboardComponentTest {

  private static String pagePath(String service) {
    return "/services/ws-test/" + service;
  }

  private static String newPath(String service) {
    return pagePath(service) + "/instances/new";
  }

  private static String addPath(String service) {
    return pagePath(service) + "/instances";
  }

  @Test
  void servicePageOpensTheAddForm() {
    String grafana = newServiceName("grafana");
    create(grafana);

    assertFormPage(pagePath(grafana), newPath(grafana), "Add Static Instance", addPath(grafana));
  }

  @Test
  void addFormAsksForHostPortCheckTagsAndMetaButNoName() {
    String grafana = newServiceName("grafana");
    create(grafana);

    String form = formTo(getPage(newPath(grafana)).getBody(), addPath(grafana));

    assertThat(form)
        .contains("name=\"host\"")
        .contains("placeholder=\"" + lanAddress() + "\"")
        .contains("name=\"port\"")
        .contains("name=\"check\"")
        .contains("name=\"tags\"")
        .contains("name=\"meta\"")
        .doesNotContain("name=\"name\"")
        .doesNotContain("name=\"container\"");
  }

  @Test
  void addingLeadsBackToTheServicePageWithTheNewInstance() {
    String grafana = newServiceName("grafana");
    create(grafana);

    ResponseEntity<String> response =
        postForm(addPath(grafana), Map.of("host", "10.1.2.3", "port", "3000"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER);
    assertThat(response.getHeaders().getLocation()).hasPath(pagePath(grafana));
    assertThat(getPage(pagePath(grafana)).getBody()).contains("10.1.2.3:3000");
    assertThat(get(servicePath(grafana)).getBody().has("publication")).isFalse();
  }

  @Test
  void serviceCanHaveManyStaticInstances() {
    String api = newServiceName("api");
    create(api);

    postForm(addPath(api), Map.of("host", "10.1.2.3", "port", "8080"));
    postForm(addPath(api), Map.of("host", "10.1.2.3", "port", "8081"));

    JsonNode instances = get(servicePath(api)).getBody().get("instances");
    assertThat(instances.valueStream().map(instance -> instance.get("port").asInt()))
        .containsExactlyInAnyOrder(8080, 8081);
    assertThat(getPage(pagePath(api)).getBody())
        .contains("10.1.2.3:8080")
        .contains("10.1.2.3:8081");
  }

  @Test
  void emptyHostAddsAtTheLanAddress() {
    String api = newServiceName("api");
    create(api);

    postForm(addPath(api), Map.of("host", "", "port", "8080"));

    assertThat(getPage(pagePath(api)).getBody()).contains(lanAddress() + ":8080");
  }

  @Test
  void addingWithAnHttpCheckTagsAndMeta() {
    String api = newServiceName("api");
    create(api);

    postForm(
        addPath(api),
        Map.of(
            "host",
            "10.1.2.3",
            "port",
            "8080",
            "check",
            "http",
            "path",
            "/health",
            "interval",
            "5s",
            "timeout",
            "1s",
            "tags",
            "backend, v2",
            "meta",
            "owner=pablo\r\nteam = core\r\n"));

    JsonNode instance = get(servicePath(api)).getBody().get("instances").get(0);
    assertThat(instance.get("check").get("type").asString()).isEqualTo("http");
    assertThat(instance.get("check").get("path").asString()).isEqualTo("/health");
    assertThat(instance.get("tags").valueStream().map(JsonNode::asString))
        .containsExactly("backend", "v2");
    assertThat(instance.get("meta").get("team").asString()).isEqualTo("core");
  }

  @Test
  void badPortIsShownAtItsFieldWithWhatWasTyped() {
    String api = newServiceName("api");
    create(api);

    ResponseEntity<String> response =
        postForm(addPath(api), Map.of("host", "10.1.2.3", "port", "http"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).contains("<h1>Add Static Instance</h1>");
    String form = formTo(response.getBody(), addPath(api));
    assertThat(openingTag(form, "name=\"port\""))
        .contains("aria-invalid=\"true\"")
        .contains("value=\"http\"");
    assertThat(form).contains("The port http is not a TCP port").contains("value=\"10.1.2.3\"");
    assertThat(get(servicePath(api)).getBody().get("instances")).isEmpty();
  }

  @Test
  void badMetaLineIsShownAtItsField() {
    String api = newServiceName("api");
    create(api);

    ResponseEntity<String> response =
        postForm(addPath(api), Map.of("host", "", "port", "8080", "meta", "owner pablo"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).contains("key=value").contains("owner pablo");
  }

  @Test
  void missingServiceIsRefusedAndNotCreated() {
    String web = newServiceName("web");

    assertThat(getPage(newPath(web)).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(postForm(addPath(web), Map.of("port", "8080")).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(get(servicePath(web)).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void remoteServicePageHasNoAddForm() {
    String api = newServiceName("api");
    remote.register(api, Map.of("host", "10.9.8.7", "port", 9090));
    remote.publish(api, "yard");
    String remotePage = "/services/" + RemoteWorkstation.NAME + "/" + api;

    assertThat(getPage(remotePage).getBody()).doesNotContain("/instances/new");
    assertThat(getPage(remotePage + "/instances/new").getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(postForm(remotePage + "/instances", Map.of("port", "8080")).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void eachStaticInstanceHasAnEditLinkAndARemoveButton() {
    String api = newServiceName("api");
    String first = register(api, Map.of("host", "10.1.2.3", "port", 8080)).get("id").asString();
    String second = register(api, Map.of("host", "10.1.2.3", "port", 8081)).get("id").asString();

    String page = getPage(pagePath(api)).getBody();

    for (String id : new String[] {first, second}) {
      String instance = pagePath(api) + "/instances/" + id;
      assertThat(openingTag(page, "href=\"" + instance + "/edit\"")).startsWith("<a");
      assertThat(formTo(page, instance + "/remove"))
          .contains("aria-label=\"Remove Static Instance")
          .doesNotContain(">Remove<");
    }
  }

  @Test
  void removeButtonComesAfterTheButtonsThatMoveTheSource() {
    String api = newServiceName("api");
    String first = register(api, Map.of("host", "10.1.2.3", "port", 8080)).get("id").asString();
    String second = register(api, Map.of("host", "10.1.2.3", "port", 8081)).get("id").asString();

    String page = getPage(pagePath(api)).getBody();

    String item =
        page.substring(
            page.indexOf("data-source=\"" + first + "\""),
            page.indexOf("data-source=\"" + second + "\""));
    assertThat(item.indexOf("/remove")).isGreaterThan(item.lastIndexOf(" down\""));
  }

  @Test
  void editingKeepsTheInstanceAndItsIdAndLeadsBackToTheServicePage() {
    String api = newServiceName("api");
    String id = register(api, Map.of("host", "10.1.2.3", "port", 8080)).get("id").asString();
    String edit = pagePath(api) + "/instances/" + id + "/edit";

    assertThat(formTo(getPage(edit).getBody(), edit)).contains("value=\"8080\"");
    ResponseEntity<String> response = postForm(edit, Map.of("host", "10.1.2.3", "port", "9090"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER);
    assertThat(response.getHeaders().getLocation()).hasPath(pagePath(api));
    JsonNode instances = get(servicePath(api)).getBody().get("instances");
    assertThat(instances).hasSize(1);
    assertThat(instances.get(0).get("id").asString()).isEqualTo(id);
    assertThat(instances.get(0).get("port").asInt()).isEqualTo(9090);
  }

  @Test
  void removingOneStaticInstanceKeepsTheOthersAndTheService() {
    String api = newServiceName("api");
    String first = register(api, Map.of("host", "10.1.2.3", "port", 8080)).get("id").asString();
    register(api, Map.of("host", "10.1.2.3", "port", 8081));

    ResponseEntity<String> response =
        postForm(pagePath(api) + "/instances/" + first + "/remove", Map.of());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER);
    assertThat(response.getHeaders().getLocation()).hasPath(pagePath(api));
    String page = getPage(pagePath(api)).getBody();
    assertThat(page).contains("10.1.2.3:8081").doesNotContain("10.1.2.3:8080");
  }

  @Test
  void removingTheLastStaticInstanceLeavesAnEmptyService() {
    String api = newServiceName("api");
    String id = register(api, Map.of("host", "10.1.2.3", "port", 8080)).get("id").asString();

    postForm(pagePath(api) + "/instances/" + id + "/remove", Map.of());

    JsonNode service = get(servicePath(api)).getBody();
    assertThat(service.get("instances")).isEmpty();
    assertThat(service.get("empty").asBoolean()).isTrue();
    assertThat(getPage(pagePath(api)).getBody()).contains("href=\"" + newPath(api) + "\"");
  }

  @Test
  void menuHasNoStaticInstancesLink() {
    assertThat(getPage("/").getBody())
        .doesNotContain("href=\"/static-instances\"")
        .doesNotContain(">Static Instances<");
  }
}
