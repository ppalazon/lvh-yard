package cc.lvhy.yard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** The Instance Sources card of a Service's page: the order, the up and down buttons, remove. */
class ServicePageInstanceSourcesComponentTest extends ServicePageComponentBase {

  @Test
  void instanceSourcesAreListedInOrderAndMovedWithUpAndDownButtons() {
    String web = newServiceName("web");
    String first = register(web, Map.of("host", "10.1.2.3", "port", 8080)).get("id").asString();
    String second = register(web, Map.of("host", "10.1.2.3", "port", 8081)).get("id").asString();
    String page = pagePath(web);

    String sources = div(getPage(page).getBody(), "id=\"sources\"");

    assertInOrder(sources, "10.1.2.3:8080", "10.1.2.3:8081");
    assertThat(sources).contains("name=\"order\" value=\"" + second + "," + first + "\"");
    assertThat(sources).contains("aria-label=\"Move Static Instance 10.1.2.3:8080 down\"");

    ResponseEntity<String> response =
        postForm(page + "/instance-priority", Map.of("order", second + "," + first));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER);
    assertThat(response.getHeaders().getLocation()).hasPath(page);
    assertThat(get(servicePath(web)).getBody().get("sources").valueStream())
        .map(source -> source.get("id").asString())
        .containsExactly(second, first);
  }

  @Test
  void toggleOffHidesTheUpAndDownButtonsAndKeepsTheOrder() {
    String web = newServiceName("web");
    String first = register(web, Map.of("host", "10.1.2.3", "port", 8080)).get("id").asString();
    String second = register(web, Map.of("host", "10.1.2.3", "port", 8081)).get("id").asString();
    String page = pagePath(web);

    ResponseEntity<String> response = postForm(page + "/instance-priority", Map.of("on", "false"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER);
    String sources = div(getPage(page).getBody(), "id=\"sources\"");
    assertThat(sources)
        .contains("data-instance-priority=\"off\"")
        .contains("name=\"on\" value=\"true\"")
        .doesNotContain("name=\"order\"");
    assertInOrder(sources, "10.1.2.3:8080", "10.1.2.3:8081");

    postForm(page + "/instance-priority", Map.of("on", "true"));

    assertThat(div(getPage(page).getBody(), "id=\"sources\""))
        .contains("data-instance-priority=\"on\"")
        .contains("name=\"order\" value=\"" + second + "," + first + "\"");
  }

  @Test
  void orderWithAnUnknownSourceIsRefusedAndKeepsTheOrder() {
    String web = newServiceName("web");
    String first = register(web, Map.of("host", "10.1.2.3", "port", 8080)).get("id").asString();
    String second = register(web, Map.of("host", "10.1.2.3", "port", 8081)).get("id").asString();

    ResponseEntity<String> response =
        postForm(pagePath(web) + "/instance-priority", Map.of("order", "nothing"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(get(servicePath(web)).getBody().get("sources").valueStream())
        .map(source -> source.get("id").asString())
        .containsExactly(first, second);
  }

  @Test
  void removingAnUnknownInstanceIsNotFound() {
    String web = newServiceName("web");
    register(web, Map.of("host", "10.1.2.3", "port", 8080));

    ResponseEntity<String> response =
        postForm(pagePath(web) + "/instances/nothing/remove", Map.of());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(get(servicePath(web)).getBody().get("instances")).hasSize(1);
  }
}
