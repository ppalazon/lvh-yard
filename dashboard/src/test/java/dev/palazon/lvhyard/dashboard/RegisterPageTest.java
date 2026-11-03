package dev.palazon.lvhyard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/** The Register form, which adds a Static Instance. */
class RegisterPageTest extends DashboardTest {

  @Test
  void formAsksForNameHostAndPortAndShowsTheDefaultHost() {
    ResponseEntity<String> response = getPage("/register");

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
        postForm("/register", Map.of("name", grafana, "host", "10.1.2.3", "port", "3000"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER);
    assertThat(response.getHeaders().getLocation()).hasPath("/services/ws-test/" + grafana);
    assertThat(getPage("/services/ws-test/" + grafana).getBody()).contains("10.1.2.3:3000");
  }

  @Test
  void emptyHostRegistersAtTheLanAddress() {
    String api = newServiceName("api");

    postForm("/register", Map.of("name", api, "host", "", "port", "8080"));

    assertThat(getPage("/services/ws-test/" + api).getBody()).contains(lanAddress() + ":8080");
  }

  @Test
  void invalidNameIsShownInTheFormWithWhatWasTyped() {
    ResponseEntity<String> response =
        postForm("/register", Map.of("name", "My_API", "host", "10.1.2.3", "port", "8080"));

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
        postForm("/register", Map.of("name", "yard", "host", "", "port", "8080"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).contains("taken by a Core Service");
  }

  @Test
  void portThatIsNotANumberIsShownInTheForm() {
    String api = newServiceName("api");

    ResponseEntity<String> response =
        postForm("/register", Map.of("name", api, "host", "", "port", "http"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody())
        .contains("The port http is not a TCP port")
        .contains("value=\"http\"");
  }

  @Test
  void formAsksForTheCheckTagsAndMeta() {
    assertThat(getPage("/register").getBody())
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
        "/register",
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
        postForm(
            "/register", Map.of("name", api, "host", "", "port", "8080", "meta", "owner pablo"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).contains("key=value").contains("owner pablo");
  }

  @Test
  void formOffersAContainerBindingToo() {
    assertThat(getPage("/register").getBody())
        .contains("name=\"kind\"")
        .contains("value=\"container\"")
        .contains("name=\"container\"")
        .contains("name=\"containerPort\"");
  }

  @Test
  void bindingAContainerLeadsToTheServicesPage() {
    String web = newServiceName("web");
    String name = newContainerName();

    ResponseEntity<String> response =
        postForm(
            "/register",
            Map.of("name", web, "kind", "container", "container", name, "containerPort", "8080"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER);
    assertThat(response.getHeaders().getLocation()).hasPath("/services/ws-test/" + web);
    JsonNode binding = get(servicePath(web)).getBody().get("containerBindings").get(0);
    assertThat(binding.get("container").asString()).isEqualTo(name);
    assertThat(binding.get("port").asInt()).isEqualTo(8080);
  }

  @Test
  void invalidContainerNameIsShownInTheFormWithWhatWasTyped() {
    String web = newServiceName("web");

    ResponseEntity<String> response =
        postForm(
            "/register",
            Map.of("name", web, "kind", "container", "container", "my web", "containerPort", "80"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody())
        .contains("not one Docker allows")
        .contains("value=\"my web\"")
        .contains("value=\"80\"")
        .containsPattern("value=\"container\"[^>]*checked");
  }

  @Test
  void containerPortThatIsNotANumberIsShownAtItsField() {
    String web = newServiceName("web");

    ResponseEntity<String> response =
        postForm(
            "/register",
            Map.of("name", web, "kind", "container", "container", "web", "containerPort", "http"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).contains("id=\"containerPort-error\"").contains("The port http");
  }

  @Test
  void formOffersAComposeServiceToo() {
    assertThat(getPage("/register").getBody())
        .contains("value=\"compose\"")
        .contains("name=\"composeProject\"")
        .contains("name=\"composeService\"");
  }

  @Test
  void bindingAComposeServiceLeadsToTheServicesPage() {
    String web = newServiceName("web");
    String project = newComposeProject();

    ResponseEntity<String> response =
        postForm(
            "/register",
            Map.of(
                "name",
                web,
                "kind",
                "compose",
                "composeProject",
                project,
                "composeService",
                "web",
                "containerPort",
                "8080",
                "container",
                "ignored"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER);
    assertThat(response.getHeaders().getLocation()).hasPath("/services/ws-test/" + web);
    JsonNode binding = get(servicePath(web)).getBody().get("containerBindings").get(0);
    assertThat(binding.get("composeProject").asString()).isEqualTo(project);
    assertThat(binding.get("composeService").asString()).isEqualTo("web");
    assertThat(binding.get("port").asInt()).isEqualTo(8080);
  }

  @Test
  void missingComposeServiceIsShownAtItsFieldWithWhatWasTyped() {
    String web = newServiceName("web");

    ResponseEntity<String> response =
        postForm(
            "/register",
            Map.of(
                "name", web, "kind", "compose", "composeProject", "shop", "containerPort", "80"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody())
        .contains("id=\"composeService-error\"")
        .contains("value=\"shop\"")
        .containsPattern("value=\"compose\"[^>]*checked");
  }
}
