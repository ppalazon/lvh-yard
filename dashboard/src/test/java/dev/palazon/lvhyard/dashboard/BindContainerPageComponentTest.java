package dev.palazon.lvhyard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.palazon.lvhyard.dashboard.docker.Docker.Container;
import dev.palazon.lvhyard.dashboard.docker.FakeDocker;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/**
 * The "Bind container" form of the Service page, with its container selector. Other tests run
 * containers on the same Docker, so each test looks only at its own.
 */
class BindContainerPageComponentTest extends DashboardComponentTest {

  private static String servicePage(String service) {
    return "/services/ws-test/" + service;
  }

  private static String bindPage(String service) {
    return servicePage(service) + "/container-bindings/new";
  }

  private static String bindAction(String service) {
    return servicePage(service) + "/container-bindings";
  }

  /** The option of the container field whose value is {@code value}. */
  private static String option(String page, String value) {
    return openingTag(page, "data-value=\"" + value + "\"");
  }

  @Test
  void servicePageOpensTheBindFormOnItsOwnPage() {
    String web = newServiceName("web");
    create(web);

    String page = getPage(servicePage(web)).getBody();

    String link = openingTag(page, "href=\"" + bindPage(web) + "\"");
    assertThat(page).contains(link + "Bind container</a>");
    ResponseEntity<String> response = getPage(bindPage(web));
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    String form = formTo(response.getBody(), bindAction(web));
    assertThat(openingTag(form, "action=\"" + bindAction(web) + "\"")).contains("method=\"post\"");
    assertThat(form)
        .contains("name=\"target\"")
        .contains("name=\"containerPort\"")
        .contains("type=\"submit\"")
        .contains("href=\"" + servicePage(web) + "\"");
  }

  @Test
  void containerFieldListsComposeServicesThenContainersWithNoComposeProject() {
    String project = newComposeProject();
    String composed = newContainerName();
    String plain = newContainerName();
    Container stopped = start(FakeDocker.container(newContainerName()).port(80));
    start(FakeDocker.container(composed).compose(project, "web").port(80).port(9000));
    start(FakeDocker.container(plain).port(3000));
    stopContainer(stopped);
    String web = newServiceName("web");
    create(web);

    String page = getPage(bindPage(web)).getBody();

    assertThat(option(page, project + "/web")).contains("data-ports=\"80 9000\"");
    assertThat(option(page, plain)).contains("data-ports=\"3000\"");
    assertThat(page).contains("data-value=\"" + stopped.name() + "\"");
    assertThat(page).doesNotContain("data-value=\"" + composed + "\"");
    assertThat(page.indexOf("data-value=\"" + project + "/web\""))
        .isLessThan(page.indexOf("Containers</li>"))
        .isLessThan(page.indexOf("data-value=\"" + plain + "\""));
  }

  @Test
  void followedTargetShowsButCannotBeSelected() {
    String name = newContainerName();
    start(FakeDocker.container(name).port(80));
    String other = newServiceName("other");
    bind(other, name, 80);
    String web = newServiceName("web");
    create(web);

    String page = getPage(bindPage(web)).getBody();

    assertThat(option(page, name)).contains("aria-disabled=\"true\"");
    String item = page.substring(page.indexOf("data-value=\"" + name + "\""));
    assertThat(item.substring(0, item.indexOf("</li>"))).contains(other);
  }

  @Test
  void typedContainerThatDoesNotExistIsBound() {
    String name = newContainerName();
    String web = newServiceName("web");
    create(web);

    ResponseEntity<String> response =
        postForm(bindAction(web), Map.of("target", name, "containerPort", "8080"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER);
    assertThat(response.getHeaders().getLocation()).hasPath(servicePage(web));
    assertThat(get(servicePath(web)).getBody().get("containerBindings").get(0).get("id").asString())
        .isEqualTo(name + ":8080");
  }

  @Test
  void typedComposeProjectAndServiceThatDoesNotExistIsBound() {
    String project = newComposeProject();
    String web = newServiceName("web");
    create(web);

    ResponseEntity<String> response =
        postForm(bindAction(web), Map.of("target", project + "/api", "containerPort", "80"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER);
    JsonNode service = get(servicePath(web)).getBody();
    assertThat(service.get("containerBindings").get(0).get("id").asString())
        .isEqualTo("compose:" + project + ":api:80");
    assertThat(service.get("state").asString()).isEqualTo("unpublished");
  }

  @Test
  void typedPortThatTheContainerDoesNotListIsBound() {
    String name = newContainerName();
    start(FakeDocker.container(name).port(80));
    String web = newServiceName("web");
    create(web);

    ResponseEntity<String> response =
        postForm(bindAction(web), Map.of("target", name, "containerPort", "4321"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER);
    assertThat(get(servicePath(web)).getBody().get("containerBindings").get(0).get("port").asInt())
        .isEqualTo(4321);
  }

  @Test
  void refusedFormKeepsWhatWasTypedAndListsThePortsOfThePick() {
    String name = newContainerName();
    start(FakeDocker.container(name).port(80).port(9000));
    String web = newServiceName("web");
    create(web);

    ResponseEntity<String> response =
        postForm(bindAction(web), Map.of("target", name, "containerPort", ""));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    String page = response.getBody();
    assertThat(openingTag(page, "name=\"target\"")).contains("value=\"" + name + "\"");
    assertThat(openingTag(page, "name=\"containerPort\"")).contains("aria-invalid=\"true\"");
    String ports = page.substring(page.indexOf("id=\"containerPort-options\""));
    ports = ports.substring(0, ports.indexOf("</datalist>"));
    assertThat(ports).contains("value=\"80\"").contains("value=\"9000\"");
  }

  @Test
  void typedFollowedTargetIsRefusedAtItsField() {
    String project = newComposeProject();
    String other = newServiceName("other");
    bindCompose(other, project, "web", 80);
    String web = newServiceName("web");
    create(web);

    ResponseEntity<String> response =
        postForm(bindAction(web), Map.of("target", project + "/web", "containerPort", "9000"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(openingTag(response.getBody(), "name=\"target\"")).contains("aria-invalid=\"true\"");
    assertThat(response.getBody()).contains("of the Service " + other);
    assertThat(get(servicePath(web)).getBody().get("containerBindings")).isEmpty();
  }

  @Test
  void badNameIsRefusedAtTheContainerField() {
    String web = newServiceName("web");
    create(web);

    ResponseEntity<String> response =
        postForm(bindAction(web), Map.of("target", "Shop/web", "containerPort", "80"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(openingTag(response.getBody(), "name=\"target\"")).contains("aria-invalid=\"true\"");
  }

  @Test
  void serviceWithManyContainerBindingsShowsTheBoundInstancesOfEach() {
    String first = newContainerName();
    String second = newContainerName();
    start(FakeDocker.container(first).port(80));
    start(FakeDocker.container(second).port(80));
    String web = newServiceName("web");
    bind(web, first, 80);
    bind(web, second, 80);
    await()
        .atMost(Duration.ofSeconds(15))
        .until(() -> get(servicePath(web)).getBody().get("instances").size() == 2);

    String page = getPage(servicePage(web)).getBody();

    assertThat(InstancePriorityComponentTest.sourceItem(page, first + ":80"))
        .contains(">" + first + "</span>");
    assertThat(InstancePriorityComponentTest.sourceItem(page, second + ":80"))
        .contains(">" + second + "</span>");
  }

  @Test
  void remoteServiceHasNoBindForm() {
    String web = newServiceName("web");
    remote.create(web);

    assertThat(getPage("/services/" + RemoteWorkstation.NAME + "/" + web).getBody())
        .doesNotContain("Bind container");
    assertThat(
            getPage("/services/" + RemoteWorkstation.NAME + "/" + web + "/container-bindings/new")
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void bindFormOfAMissingServiceIsNotFound() {
    assertThat(getPage(bindPage(newServiceName("web"))).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  /** The table row that contains {@code text}. */
}
