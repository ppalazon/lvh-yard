package cc.lvhy.yard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** The Container Bindings of a Service's page: what each one gives, and remove. */
class ServicePageContainerBindingsComponentTest extends ServicePageComponentBase {

  @Test
  void showsEachContainerBindingIncludingOnesThatMatchNoContainer() {
    String web = newServiceName("web");
    String name = newContainerName();
    bind(web, name, 8080);
    bindCompose(web, newComposeProject(), "web", 80);

    String sources = div(getPage(pagePath(web)).getBody(), "id=\"sources\"");

    assertThat(sources)
        .contains(name)
        .contains("Port 8080")
        .contains("No container of this name runs")
        .contains("No container of this Compose service runs.");
  }

  @Test
  void boundInstanceSaysWhichContainerGivesItAndCannotBeEditedOrRemoved() {
    String web = newServiceName("web");
    String name = newContainerName();
    startContainer(name, 80, false);
    bind(web, name, 80);
    await()
        .atMost(Duration.ofSeconds(15))
        .until(() -> !get(servicePath(web)).getBody().get("instances").isEmpty());
    String id = get(servicePath(web)).getBody().get("instances").get(0).get("id").asString();

    String page = getPage(pagePath(web)).getBody();

    assertThat(page)
        .contains("Bound to container " + name)
        .doesNotContain("/instances/" + id + "/edit")
        .doesNotContain("/instances/" + id + "/remove");
  }

  @Test
  void removingTheLastContainerBindingStaysOnTheServicePage() {
    String web = newServiceName("web");
    String id = bind(web, newContainerName(), 80).get("id").asString();
    String removePath = pagePath(web) + "/container-bindings/" + id + "/remove";
    assertThat(getPage(pagePath(web)).getBody()).contains("action=\"" + removePath + "\"");

    ResponseEntity<String> response = postForm(removePath, Map.of());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER);
    assertThat(response.getHeaders().getLocation()).hasPath(pagePath(web));
    assertThat(get(servicePath(web)).getBody().get("containerBindings")).isEmpty();
  }

  @Test
  void removingAnUnknownContainerBindingIsNotFound() {
    String web = newServiceName("web");
    bind(web, newContainerName(), 80);

    ResponseEntity<String> response =
        postForm(pagePath(web) + "/container-bindings/nothing/remove", Map.of());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(get(servicePath(web)).getBody().get("containerBindings")).hasSize(1);
  }
}
