package dev.palazon.lvhyard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/** A Service's detail page, where its Instances are seen and removed. */
class ServicePageTest extends DashboardTest {

  @Test
  void showsTheServicesInstancesAndTheirHealth() {
    String api = newServiceName("api");
    register(api, Map.of("host", "10.1.2.3", "port", 8080));

    ResponseEntity<String> response = getPage("/services/ws-test/" + api);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody())
        .contains("<h1>" + api + "</h1>")
        .contains("10.1.2.3:8080")
        .contains("critical");
  }

  @Test
  void removingAnInstanceStaysOnTheServicesPageWhileOthersAreLeft() {
    String web = newServiceName("web");
    String first = register(web, Map.of("host", "10.1.2.3", "port", 8080)).get("id").asString();
    register(web, Map.of("host", "10.1.2.3", "port", 8081));
    String removePath = "/services/ws-test/" + web + "/instances/" + first + "/remove";
    assertThat(getPage("/services/ws-test/" + web).getBody())
        .contains("action=\"" + removePath + "\"");

    ResponseEntity<String> response = postForm(removePath, Map.of());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER);
    assertThat(response.getHeaders().getLocation()).hasPath("/services/ws-test/" + web);
    assertThat(getPage("/services/ws-test/" + web).getBody())
        .doesNotContain("10.1.2.3:8080")
        .contains("10.1.2.3:8081");
  }

  @Test
  void removingTheLastInstanceGoesBackToTheCatalog() {
    String web = newServiceName("web");
    String id = register(web, Map.of("host", "10.1.2.3", "port", 8080)).get("id").asString();

    ResponseEntity<String> response =
        postForm("/services/ws-test/" + web + "/instances/" + id + "/remove", Map.of());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER);
    assertThat(response.getHeaders().getLocation()).hasPath("/");
    assertThat(getPage("/").getBody()).doesNotContain(web);
  }

  @Test
  void unknownServiceIsNotFound() {
    ResponseEntity<String> response = getPage("/services/ws-test/" + newServiceName("nothing"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void publishingShowsTheHostnameAndTheState() {
    String web = newServiceName("web");
    register(web, Map.of("host", "10.1.2.3", "port", 8080));
    String page = "/services/ws-test/" + web;
    assertThat(getPage(page).getBody())
        .contains("Unpublished")
        .contains("action=\"" + page + "/publish\"");

    ResponseEntity<String> response = postForm(page + "/publish", Map.of());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER);
    assertThat(response.getHeaders().getLocation()).hasPath(page);
    assertThat(getPage(page).getBody())
        .contains("Unhealthy")
        .contains("href=\"http://" + web + ".lvh.test\"")
        .contains("Local")
        .contains("action=\"" + page + "/unpublish\"");
    assertThat(get(servicePath(web)).getBody().get("state").asString()).isEqualTo("unhealthy");
  }

  @Test
  void scopeCanBeChangedToYardAndBack() {
    String web = newServiceName("web");
    register(web, Map.of("host", "10.1.2.3", "port", 8080));
    publish(web);
    String page = "/services/ws-test/" + web;
    assertThat(getPage(page).getBody())
        .contains("Local Scope")
        .contains("name=\"scope\" value=\"yard\"");

    ResponseEntity<String> response = postForm(page + "/publish", Map.of("scope", "yard"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER);
    assertThat(response.getHeaders().getLocation()).hasPath(page);
    assertThat(getPage(page).getBody())
        .contains("Yard Scope")
        .contains("href=\"http://" + web + ".lvh.test\"")
        .contains(web + ".ws-test.&lt;base-domain&gt;")
        .contains("name=\"scope\" value=\"local\"");
    assertThat(get(servicePath(web)).getBody().get("publication").get("scope").asString())
        .isEqualTo("yard");

    postForm(page + "/publish", Map.of("scope", "local"));

    assertThat(get(servicePath(web)).getBody().get("publication").get("scope").asString())
        .isEqualTo("local");
  }

  @Test
  void instancePriorityIsEditedByMovingAKindUpOrDownOrRemovingIt() {
    String web = newServiceName("web");
    register(web, Map.of("host", "10.1.2.3", "port", 8080));
    publish(web);
    String page = "/services/ws-test/" + web;
    assertThat(getPage(page).getBody())
        .contains("Instance Priority")
        .contains("aria-label=\"Move Bound up\"")
        .contains("name=\"priority\" value=\"bound,static,alias\"")
        .contains("aria-label=\"Move Bound down\"")
        .contains("name=\"priority\" value=\"static,alias,bound\"")
        .contains("aria-label=\"Remove Bound\"")
        .contains("name=\"priority\" value=\"static,alias\"")
        .doesNotContain("Move Static up")
        .doesNotContain("Move Alias down");

    ResponseEntity<String> response = postForm(page + "/publish", Map.of("priority", "bound"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER);
    assertThat(response.getHeaders().getLocation()).hasPath(page);
    assertThat(get(servicePath(web)).getBody().get("priority").valueStream())
        .extracting(JsonNode::asString)
        .containsExactly("bound");
    assertThat(getPage(page).getBody())
        .as("the last kind cannot be removed, and a removed one can be added back")
        .doesNotContain("aria-label=\"Remove Bound\"")
        .contains("aria-label=\"Add Static\"")
        .contains("name=\"priority\" value=\"bound,static\"");
  }

  @Test
  void instancePriorityWithAnUnknownKindIsRefused() {
    String web = newServiceName("web");
    register(web, Map.of("host", "10.1.2.3", "port", 8080));

    ResponseEntity<String> response =
        postForm("/services/ws-test/" + web + "/publish", Map.of("priority", "static,docker"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void emptyInstancePriorityIsRefused() {
    String web = newServiceName("web");
    register(web, Map.of("host", "10.1.2.3", "port", 8080));
    publish(web);

    ResponseEntity<String> response =
        postForm("/services/ws-test/" + web + "/publish", Map.of("priority", " "));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void unknownScopeIsRefused() {
    String web = newServiceName("web");
    register(web, Map.of("host", "10.1.2.3", "port", 8080));

    ResponseEntity<String> response =
        postForm("/services/ws-test/" + web + "/publish", Map.of("scope", "world"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void yardScopedServiceWarnsAboutAnInstanceOnLoopback() {
    String web = newServiceName("web");
    register(web, Map.of("host", "127.0.0.1", "port", 8080));
    String page = "/services/ws-test/" + web;
    assertThat(getPage(page).getBody()).doesNotContain("loopback");

    put(publicationPath(web), Map.of("scope", "yard"));

    assertThat(getPage(page).getBody())
        .contains("role=\"alert\"")
        .contains("127.0.0.1:8080 is on loopback")
        .as("its Home Workstation reaches it")
        .doesNotContain("Out of range");
  }

  @Test
  void yardScopedServiceWarnsAboutABoundInstanceOnAnUnpublishedContainerPort() {
    String web = newServiceName("web");
    String name = newContainerName();
    String address = containerAddress(startContainer(name, 80, false)) + ":80";
    bind(web, name, 80);
    String page = "/services/ws-test/" + web;
    await().atMost(Duration.ofSeconds(15)).until(() -> getPage(page).getBody().contains(address));
    assertThat(getPage(page).getBody()).doesNotContain("not published");

    put(publicationPath(web), Map.of("scope", "yard"));

    assertThat(getPage(page).getBody())
        .contains("role=\"alert\"")
        .contains(address + " is on a container port that is not published");
  }

  @Test
  void remoteServiceWithYardScopeShowsItsHostnameHere() {
    String api = newServiceName("api");
    remote.register(api, Map.of("host", "10.9.8.7", "port", 9090));
    remote.publish(api, "yard");

    String body = getPage("/services/" + RemoteWorkstation.NAME + "/" + api).getBody();

    assertThat(body)
        .contains("Yard Scope")
        .contains("href=\"http://" + api + "." + RemoteWorkstation.NAME + ".lvh.test\"");
  }

  @Test
  void unpublishingKeepsTheInstances() {
    String web = newServiceName("web");
    register(web, Map.of("host", "10.1.2.3", "port", 8080));
    publish(web);
    String page = "/services/ws-test/" + web;

    ResponseEntity<String> response = postForm(page + "/unpublish", Map.of());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER);
    assertThat(response.getHeaders().getLocation()).hasPath(page);
    assertThat(getPage(page).getBody())
        .contains("Unpublished")
        .contains("10.1.2.3:8080")
        .doesNotContain(web + ".lvh.test");
  }

  @Test
  void unpublishingServiceWithoutInstancesGoesBackToTheCatalog() {
    String web = newServiceName("web");
    String id = register(web, Map.of("host", "10.1.2.3", "port", 8080)).get("id").asString();
    publish(web);
    delete(instancesPath(web) + "/" + id);
    assertThat(getPage("/services/ws-test/" + web).getBody()).contains("Unavailable");

    ResponseEntity<String> response = postForm("/services/ws-test/" + web + "/unpublish", Map.of());

    assertThat(response.getHeaders().getLocation()).hasPath("/");
  }

  @Test
  void publishingAnUnknownServiceIsNotFound() {
    ResponseEntity<String> response =
        postForm("/services/ws-test/" + newServiceName("nothing") + "/publish", Map.of());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void showsEachChecksLastOutput() {
    String api = newServiceName("api");
    answerHttpOnLanAddress(18083, "503 Service Unavailable", "warming up");
    register(api, Map.of("port", 18083, "check", Map.of("path", "/health", "interval", "1s")));

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() ->
            assertThat(getPage("/services/ws-test/" + api).getBody())
                .contains("HTTP GET /health")
                .contains("503")
                .contains("warming up"));
  }

  @Test
  void showsTheInstancesTagsAndMeta() {
    String api = newServiceName("api");
    register(
        api,
        Map.of(
            "host",
            "10.1.2.3",
            "port",
            8080,
            "tags",
            List.of("backend"),
            "meta",
            Map.of("owner", "pablo")));

    assertThat(getPage("/services/ws-test/" + api).getBody())
        .contains("backend")
        .contains("owner")
        .contains("pablo");
  }

  @Test
  void editFormHoldsTheInstanceAsItIs() {
    String api = newServiceName("api");
    String id =
        register(
                api,
                Map.of(
                    "host",
                    "10.1.2.3",
                    "port",
                    8080,
                    "check",
                    Map.of("path", "/health", "interval", "5s"),
                    "tags",
                    List.of("a", "b"),
                    "meta",
                    Map.of("owner", "pablo")))
            .get("id")
            .asString();
    String editPath = "/services/ws-test/" + api + "/instances/" + id + "/edit";
    assertThat(getPage("/services/ws-test/" + api).getBody()).contains("href=\"" + editPath + "\"");

    ResponseEntity<String> response = getPage(editPath);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody())
        .contains("value=\"10.1.2.3\"")
        .contains("value=\"8080\"")
        .contains("value=\"/health\"")
        .contains("value=\"5s\"")
        .contains("value=\"2s\"")
        .contains("value=\"a, b\"")
        .contains("owner=pablo")
        .contains("<option value=\"http\" selected");
  }

  @Test
  void editingAnInstanceKeepsItAndShowsItsService() {
    String api = newServiceName("api");
    String id = register(api, Map.of("host", "10.1.2.3", "port", 8080)).get("id").asString();
    String editPath = "/services/ws-test/" + api + "/instances/" + id + "/edit";

    ResponseEntity<String> response =
        postForm(
            editPath,
            Map.of(
                "host",
                "10.1.2.4",
                "port",
                "9090",
                "check",
                "tcp",
                "path",
                "",
                "interval",
                "",
                "timeout",
                "",
                "tags",
                "",
                "meta",
                ""));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER);
    assertThat(response.getHeaders().getLocation()).hasPath("/services/ws-test/" + api);
    JsonNode instances = get(servicePath(api)).getBody().get("instances");
    assertThat(instances).hasSize(1);
    assertThat(instances.get(0).get("id").asString()).isEqualTo(id);
    assertThat(instances.get(0).get("port").asInt()).isEqualTo(9090);
  }

  @Test
  void invalidEditIsShownInTheFormWithWhatWasTyped() {
    String api = newServiceName("api");
    String id = register(api, Map.of("host", "10.1.2.3", "port", 8080)).get("id").asString();

    ResponseEntity<String> response =
        postForm(
            "/services/ws-test/" + api + "/instances/" + id + "/edit",
            Map.of(
                "host",
                "10.1.2.3",
                "port",
                "8080",
                "check",
                "http",
                "path",
                "/",
                "interval",
                "often",
                "timeout",
                "",
                "tags",
                "",
                "meta",
                ""));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody())
        .contains("not a duration")
        .contains("value=\"often\"")
        .contains("aria-invalid=\"true\"");
    assertThat(
            get(servicePath(api))
                .getBody()
                .get("instances")
                .get(0)
                .get("check")
                .get("type")
                .asString())
        .isEqualTo("tcp");
  }

  @Test
  void editingAnUnknownInstanceIsNotFound() {
    String api = newServiceName("api");
    register(api, Map.of("port", 8080));

    assertThat(getPage("/services/ws-test/" + api + "/instances/nothing/edit").getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void showsEachContainerBindingIncludingOneThatMatchesNoContainer() {
    String web = newServiceName("web");
    String name = newContainerName();
    bind(web, name, 8080);

    String page = getPage("/services/ws-test/" + web).getBody();

    assertThat(page)
        .contains("Container Bindings")
        .contains(name)
        .contains("8080")
        .contains("No container of this name runs");
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

    String page = getPage("/services/ws-test/" + web).getBody();

    assertThat(page)
        .contains("Bound to container " + name)
        .doesNotContain("/instances/" + id + "/edit")
        .doesNotContain("/instances/" + id + "/remove");
  }

  @Test
  void removingTheLastContainerBindingGoesBackToTheCatalog() {
    String web = newServiceName("web");
    String id = bind(web, newContainerName(), 80).get("id").asString();
    String removePath = "/services/ws-test/" + web + "/container-bindings/" + id + "/remove";
    assertThat(getPage("/services/ws-test/" + web).getBody())
        .contains("action=\"" + removePath + "\"");

    ResponseEntity<String> response = postForm(removePath, Map.of());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER);
    assertThat(response.getHeaders().getLocation()).hasPath("/");
    assertThat(get(servicePath(web)).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void remoteServicesContainerBindingsAreShownWithoutAForm() {
    String web = newServiceName("web");
    String name = newContainerName();
    remote.bind(web, name, 80);

    String page = getPage("/services/" + RemoteWorkstation.NAME + "/" + web).getBody();

    assertThat(page).contains(name).doesNotContain("/container-bindings/");
  }

  @Test
  void showsAComposeBindingAndEachBoundInstanceItGives() {
    String web = newServiceName("web");
    String project = newComposeProject();
    startComposeContainer(newContainerName(), project, "web", 80);
    startComposeContainer(newContainerName(), project, "web", 80);
    bindCompose(web, project, "web", 80);
    await()
        .atMost(Duration.ofSeconds(15))
        .until(() -> get(servicePath(web)).getBody().get("instances").size() == 2);

    String page = getPage("/services/ws-test/" + web).getBody();

    assertThat(page)
        .contains("Compose service web of project " + project)
        .contains("Bound to Compose service web of project " + project + ", port 80.");
  }

  @Test
  void composeBindingThatMatchesNoContainerSaysSo() {
    String web = newServiceName("web");
    bindCompose(web, newComposeProject(), "web", 80);

    assertThat(getPage("/services/ws-test/" + web).getBody())
        .contains("No container of this Compose service runs.");
  }

  @Test
  void refreshesTheServiceEveryFiveSeconds() {
    String web = newServiceName("web");
    register(web, Map.of("host", "10.1.2.3", "port", 8080));

    String region = openingTag(getPage("/services/ws-test/" + web).getBody(), "id=\"service\"");

    assertThat(region)
        .contains("hx-get=\"/services/ws-test/" + web + "\"")
        .contains("hx-trigger=\"every 5s\"")
        .contains("hx-select=\"#service\"")
        .contains("hx-swap=\"outerHTML\"")
        .as(
            "an error answer, such as while its Workstation is unreachable, leaves the page as it"
                + " is")
        .contains("hx-status:4xx=\"swap:none\"")
        .contains("hx-status:5xx=\"swap:none\"");
  }

  @Test
  void refreshKeepsTheReachabilityResultsAndDoesNotTestAgain() {
    String web = newServiceName("web");
    register(web, Map.of("host", "10.1.2.3", "port", 8080));

    String page = getPage("/services/ws-test/" + web + "/reachability").getBody();

    assertThat(openingTag(page, "id=\"service\""))
        .contains("hx-get=\"/services/ws-test/" + web + "\"");
    assertThat(div(page, "id=\"service\""))
        .contains("10.1.2.3:8080")
        .doesNotContain("Reachability from");
    assertThat(page).contains("Reachability from").contains("Test again");
  }
}
