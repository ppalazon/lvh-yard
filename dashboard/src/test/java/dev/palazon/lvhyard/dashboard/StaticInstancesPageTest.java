package dev.palazon.lvhyard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/** The Static Instances page: its list, and its form that adds a Static Instance. */
class StaticInstancesPageTest extends DashboardTest {

  private static final String PATH = "/static-instances";

  @Test
  void oldRegisterAddressRedirectsHere() {
    ResponseEntity<String> response = getPage("/register");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.MOVED_PERMANENTLY);
    assertThat(response.getHeaders().getLocation()).hasPath(PATH);
  }

  @Test
  void listsEachStaticInstanceWithItsServiceAddressHealthAndEditLink() {
    String grafana = newServiceName("grafana");
    String id = register(grafana, Map.of("host", "10.1.2.3", "port", 3000)).get("id").asString();

    String page = getPage(PATH).getBody();

    assertThat(page)
        .contains("href=\"/services/ws-test/" + grafana + "\"")
        .contains("10.1.2.3:3000")
        .contains(badge("destructive", "critical"))
        .contains("href=\"/services/ws-test/" + grafana + "/instances/" + id + "/edit\"");
  }

  @Test
  void formHasAPublishCheckboxThatIsOnByDefault() {
    assertThat(openingTag(getPage(PATH).getBody(), "name=\"publish\""))
        .contains("type=\"checkbox\"")
        .contains("checked");
  }

  @Test
  void formHasNoKindChoiceAndNoContainerFields() {
    assertThat(getPage(PATH).getBody())
        .doesNotContain("name=\"kind\"")
        .doesNotContain("name=\"container\"")
        .doesNotContain("name=\"composeProject\"")
        .doesNotContain("name=\"containerPort\"");
  }

  @Test
  void registeringWithPublishCheckedPublishesANewService() {
    String web = newServiceName("web");

    postForm(PATH, Map.of("name", web, "port", "8080", "publish", "on"));

    JsonNode service = get(servicePath(web)).getBody();
    assertThat(service.get("publication").get("scope").asString()).isEqualTo("local");
  }

  @Test
  void registeringWithPublishClearedLeavesANewServiceUnpublished() {
    String web = newServiceName("web");

    postForm(PATH, Map.of("name", web, "port", "8080"));

    assertThat(get(servicePath(web)).getBody().has("publication")).isFalse();
  }

  @Test
  void clearedPublishCheckboxStaysClearedWhenTheFormIsRefused() {
    ResponseEntity<String> response = postForm(PATH, Map.of("name", "My_API", "port", "8080"));

    assertThat(openingTag(response.getBody(), "name=\"publish\"")).doesNotContain("checked");
  }

  @Test
  void formAsksForNameHostAndPortAndShowsTheDefaultHost() {
    ResponseEntity<String> response = getPage(PATH);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody())
        .contains("name=\"name\"")
        .contains("name=\"host\"")
        .contains("name=\"port\"")
        .contains("placeholder=\"" + lanAddress() + "\"");
  }

  @Test
  void registeringLeadsToTheServicesPage() {
    String grafana = newServiceName("grafana");

    ResponseEntity<String> response =
        postForm(PATH, Map.of("name", grafana, "host", "10.1.2.3", "port", "3000"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER);
    assertThat(response.getHeaders().getLocation()).hasPath("/services/ws-test/" + grafana);
    assertThat(getPage("/services/ws-test/" + grafana).getBody()).contains("10.1.2.3:3000");
  }

  @Test
  void emptyHostRegistersAtTheLanAddress() {
    String api = newServiceName("api");

    postForm(PATH, Map.of("name", api, "host", "", "port", "8080"));

    assertThat(getPage("/services/ws-test/" + api).getBody()).contains(lanAddress() + ":8080");
  }

  @Test
  void invalidNameIsShownInTheFormWithWhatWasTyped() {
    ResponseEntity<String> response =
        postForm(PATH, Map.of("name", "My_API", "host", "10.1.2.3", "port", "8080"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody())
        .contains("single DNS label")
        .contains("aria-invalid=\"true\"")
        .contains("value=\"My_API\"")
        .contains("value=\"10.1.2.3\"")
        .contains("value=\"8080\"");
  }

  @Test
  void reservedNameIsShownInTheForm() {
    ResponseEntity<String> response =
        postForm(PATH, Map.of("name", "consul", "host", "", "port", "8080"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).contains("own entry in its catalog");
  }

  @Test
  void portThatIsNotANumberIsShownInTheForm() {
    String api = newServiceName("api");

    ResponseEntity<String> response =
        postForm(PATH, Map.of("name", api, "host", "", "port", "http"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody())
        .contains("The port http is not a TCP port")
        .contains("value=\"http\"");
  }

  @Test
  void formAsksForTheCheckTagsAndMeta() {
    assertThat(getPage(PATH).getBody())
        .contains("name=\"check\"")
        .contains("name=\"path\"")
        .contains("name=\"interval\"")
        .contains("name=\"timeout\"")
        .contains("name=\"tags\"")
        .contains("name=\"meta\"")
        .contains("<option value=\"http\"")
        .contains("<option value=\"tcp\"")
        .doesNotContain("<option value=\"script\"");
  }

  @Test
  void registeringWithAnHttpCheckTagsAndMeta() {
    String api = newServiceName("api");

    postForm(
        PATH,
        Map.of(
            "name",
            api,
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
    assertThat(instance.get("check").get("interval").asString()).isEqualTo("5s");
    assertThat(instance.get("check").get("timeout").asString()).isEqualTo("1s");
    assertThat(instance.get("tags").valueStream().map(JsonNode::asString))
        .containsExactly("backend", "v2");
    assertThat(instance.get("meta").get("owner").asString()).isEqualTo("pablo");
    assertThat(instance.get("meta").get("team").asString()).isEqualTo("core");
  }

  @Test
  void metaLineWithoutAnEqualsSignIsShownInTheForm() {
    String api = newServiceName("api");

    ResponseEntity<String> response =
        postForm(PATH, Map.of("name", api, "host", "", "port", "8080", "meta", "owner pablo"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).contains("key=value").contains("owner pablo");
  }
}
