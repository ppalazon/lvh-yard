package cc.lvhy.yard.dashboard;

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
class ServicePageComponentTest extends DashboardComponentTest {

  /** The risks box, up to its own closing tag. */
  private static String risks(String page) {
    int start = page.lastIndexOf("<details", page.indexOf("id=\"risks\""));
    if (start < 0) {
      throw new AssertionError("No risks box");
    }
    return page.substring(start, page.indexOf("</details>", start) + "</details>".length());
  }

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
  void removingTheLastInstanceStaysOnTheServicePage() {
    String web = newServiceName("web");
    String id = register(web, Map.of("host", "10.1.2.3", "port", 8080)).get("id").asString();

    ResponseEntity<String> response =
        postForm("/services/ws-test/" + web + "/instances/" + id + "/remove", Map.of());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER);
    assertThat(response.getHeaders().getLocation()).hasPath("/services/ws-test/" + web);
    assertThat(get(servicePath(web)).getBody().get("empty").asBoolean()).isTrue();
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
        .contains(web + ".lvh.test")
        .contains("Local")
        .contains("action=\"" + page + "/unpublish\"");
    assertThat(get(servicePath(web)).getBody().get("state").asString()).isEqualTo("unhealthy");
  }

  @Test
  void hostnameOpensInANewTab() {
    String web = newServiceName("web");
    register(web, Map.of("host", "10.1.2.3", "port", 8080));
    publish(web);

    String page = getPage("/services/ws-test/" + web).getBody();

    assertThat(openingTag(page, "href=\"http://" + web + ".lvh.test"))
        .contains("target=\"_blank\"")
        .contains("rel=\"noopener noreferrer\"");
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
        .contains(web + ".ws-test.&lt;base-domain&gt;")
        .contains("name=\"scope\" value=\"local\"");
    assertThat(get(servicePath(web)).getBody().get("publication").get("scope").asString())
        .isEqualTo("yard");

    postForm(page + "/publish", Map.of("scope", "local"));

    assertThat(get(servicePath(web)).getBody().get("publication").get("scope").asString())
        .isEqualTo("local");
  }

  @Test
  void accessLogCanBeTurnedOnAndOff() {
    String web = newServiceName("web");
    register(web, Map.of("host", "10.1.2.3", "port", 8080));
    publish(web);
    String page = "/services/ws-test/" + web;
    assertThat(getPage(page).getBody())
        .contains("Show in the access log")
        .contains("name=\"accessLog\" value=\"true\"")
        .doesNotContain("lvh-yard ws compose logs traefik");

    ResponseEntity<String> response = postForm(page + "/publish", Map.of("accessLog", "true"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER);
    assertThat(getPage(page).getBody())
        .contains("name=\"accessLog\" value=\"false\"")
        .contains(
            "Traefik logs each request to the Route. To see the log, run this command:"
                + "<code class=\"mt-1 block w-fit max-w-full break-all rounded bg-muted px-2 py-1"
                + " font-mono\">lvh-yard ws compose logs traefik</code>");

    postForm(page + "/publish", Map.of("accessLog", "false"));

    assertThat(get(servicePath(web)).getBody().get("publication").get("accessLog").asBoolean())
        .isFalse();
  }

  @Test
  void publicationSwitchesShowTheirStateAndPostTheOtherValue() {
    String web = newServiceName("web");
    register(web, Map.of("host", "10.1.2.3", "port", 8080));
    String page = "/services/ws-test/" + web;

    String unpublished = div(getPage(page).getBody(), "id=\"publication\"");

    assertThat(unpublished.split("role=\"switch\"", -1)).hasSize(2);
    assertThat(unpublished).doesNotContain("checked");

    publish(web);
    put(publicationPath(web), Map.of("scope", "yard"));
    String published = div(getPage(page).getBody(), "id=\"publication\"");

    assertThat(published.split("role=\"switch\"", -1)).hasSize(4);
    // Published and Share with the Yard are on. Show in the access log is off.
    assertThat(published.split("checked", -1)).hasSize(3);
    assertThat(published)
        .contains("name=\"scope\" value=\"local\"")
        .contains("name=\"accessLog\" value=\"true\"");
  }

  @Test
  void instanceSourcesAreListedInOrderAndMovedWithUpAndDownButtons() {
    String web = newServiceName("web");
    String first = register(web, Map.of("host", "10.1.2.3", "port", 8080)).get("id").asString();
    String second = register(web, Map.of("host", "10.1.2.3", "port", 8081)).get("id").asString();
    String page = "/services/ws-test/" + web;

    String sources = div(getPage(page).getBody(), "id=\"sources\"");

    assertThat(sources.indexOf("10.1.2.3:8080")).isLessThan(sources.indexOf("10.1.2.3:8081"));
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
    String page = "/services/ws-test/" + web;

    ResponseEntity<String> response = postForm(page + "/instance-priority", Map.of("on", "false"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER);
    String sources = div(getPage(page).getBody(), "id=\"sources\"");
    assertThat(sources)
        .contains("data-instance-priority=\"off\"")
        .contains("name=\"on\" value=\"true\"")
        .doesNotContain("name=\"order\"");
    assertThat(sources.indexOf("10.1.2.3:8080")).isLessThan(sources.indexOf("10.1.2.3:8081"));

    postForm(page + "/instance-priority", Map.of("on", "true"));

    assertThat(div(getPage(page).getBody(), "id=\"sources\""))
        .contains("data-instance-priority=\"on\"")
        .contains("name=\"order\" value=\"" + second + "," + first + "\"");
  }

  @Test
  void orderWithAnUnknownSourceIsRefused() {
    String web = newServiceName("web");
    register(web, Map.of("host", "10.1.2.3", "port", 8080));

    ResponseEntity<String> response =
        postForm("/services/ws-test/" + web + "/instance-priority", Map.of("order", "nothing"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void addStaticInstanceLinkIsInTheInstanceSourcesCard() {
    String web = newServiceName("web");
    create(web);

    assertThat(div(getPage("/services/ws-test/" + web).getBody(), "id=\"sources\""))
        .contains("href=\"/services/ws-test/" + web + "/instances/new\"");
  }

  @Test
  void remoteServiceShowsItsOrderedSourcesWithoutButtons() {
    String api = newServiceName("api");
    String first =
        remote.register(api, Map.of("host", "10.9.8.7", "port", 9090)).get("id").asString();
    String second =
        remote.register(api, Map.of("host", "10.9.8.7", "port", 9091)).get("id").asString();
    remote.setInstancePriority(api, Map.of("order", List.of(second)));

    String sources =
        div(getPage("/services/" + RemoteWorkstation.NAME + "/" + api).getBody(), "id=\"sources\"");

    assertThat(sources.indexOf("data-source=\"" + second + "\""))
        .isNotNegative()
        .isLessThan(sources.indexOf("data-source=\"" + first + "\""));
    assertThat(sources).doesNotContain("<form").doesNotContain("<button");
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

    String body = getPage(page).getBody();
    assertThat(risks(body))
        .contains("1 risk to keep in mind")
        .contains("127.0.0.1:8080 is on loopback");
    assertThat(body).as("its Home Workstation reaches it").doesNotContain("Out of range");
  }

  @Test
  void risksAreCountedAndAreNotARedAlert() {
    String web = newServiceName("web");
    register(web, Map.of("host", "127.0.0.1", "port", 8080));
    register(web, Map.of("host", "127.0.0.1", "port", 8081));
    put(publicationPath(web), Map.of("scope", "yard"));

    String body = getPage("/services/ws-test/" + web).getBody();

    assertThat(risks(body))
        .contains("2 risks to keep in mind")
        .contains("127.0.0.1:8080 is on loopback")
        .contains("127.0.0.1:8081 is on loopback");
    assertThat(div(body, "role=\"alert\""))
        .as("a risk is not a red alert")
        .doesNotContain("loopback");
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
  void unpublishingServiceWithoutInstancesStaysOnTheServicePage() {
    String web = newServiceName("web");
    String id = register(web, Map.of("host", "10.1.2.3", "port", 8080)).get("id").asString();
    publish(web);
    delete(instancesPath(web) + "/" + id);
    assertThat(getPage("/services/ws-test/" + web).getBody()).contains("Unavailable");

    ResponseEntity<String> response = postForm("/services/ws-test/" + web + "/unpublish", Map.of());

    assertThat(response.getHeaders().getLocation()).hasPath("/services/ws-test/" + web);
  }

  @Test
  void unavailableServiceShowsARedAlert() {
    String web = newServiceName("web");
    String id = register(web, Map.of("host", "10.1.2.3", "port", 8080)).get("id").asString();
    publish(web);
    delete(instancesPath(web) + "/" + id);

    String page = getPage("/services/ws-test/" + web).getBody();

    assertThat(div(page, "role=\"alert\""))
        .contains("data-variant=\"destructive\"")
        .contains("No Instances are registered");
  }

  @Test
  void sourcesCardOfAServiceWithoutSourcesSaysSoInsteadOfAnEmptyList() {
    String web = newServiceName("web");
    String id = register(web, Map.of("host", "10.1.2.3", "port", 8080)).get("id").asString();
    publish(web);
    delete(instancesPath(web) + "/" + id);

    String sources = div(getPage("/services/ws-test/" + web).getBody(), "id=\"sources\"");

    assertThat(sources).contains("No Instance Sources yet.").doesNotContain("<table");
  }

  @Test
  void unhealthyServiceShowsARedAlert() {
    String web = newServiceName("web");
    register(web, Map.of("host", "10.1.2.3", "port", 8080));
    publish(web);

    String page = getPage("/services/ws-test/" + web).getBody();

    assertThat(div(page, "role=\"alert\""))
        .contains("data-variant=\"destructive\"")
        .contains("No Instance passes its checks");
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
        .contains(">Container</span>")
        .contains(">" + name + "</span>")
        .contains(">Port 8080</span>")
        .contains(">Unavailable</span>")
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
  void removingTheLastContainerBindingStaysOnTheServicePage() {
    String web = newServiceName("web");
    String id = bind(web, newContainerName(), 80).get("id").asString();
    String removePath = "/services/ws-test/" + web + "/container-bindings/" + id + "/remove";
    assertThat(getPage("/services/ws-test/" + web).getBody())
        .contains("action=\"" + removePath + "\"");

    ResponseEntity<String> response = postForm(removePath, Map.of());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER);
    assertThat(response.getHeaders().getLocation()).hasPath("/services/ws-test/" + web);
    assertThat(get(servicePath(web)).getBody().get("containerBindings")).isEmpty();
  }

  @Test
  void composeBindingThatMatchesNoContainerSaysSo() {
    String web = newServiceName("web");
    bindCompose(web, newComposeProject(), "web", 80);

    assertThat(getPage("/services/ws-test/" + web).getBody())
        .contains("No container of this Compose service runs.");
  }

  @Test
  void refreshesTheServiceWithYardChangedSignalAndFallbackPoll() {
    String web = newServiceName("web");
    register(web, Map.of("host", "10.1.2.3", "port", 8080));

    String region = openingTag(getPage("/services/ws-test/" + web).getBody(), "id=\"service\"");

    assertThat(region)
        .contains("hx-get=\"/services/ws-test/" + web + "\"")
        .contains("hx-trigger=\"yard-changed from:body, every 30s\"")
        .contains("hx-select=\"#service\"")
        .contains("hx-swap=\"outerHTML\"")
        .as(
            "an error answer, such as while its Workstation is unreachable, leaves the page as it"
                + " is")
        .contains("hx-status:4xx=\"swap:none\"")
        .contains("hx-status:5xx=\"swap:none\"");
  }

  @Test
  void remoteServiceWithYardScopeShowsItsHostnameHere() {
    String api = newServiceName("api");
    remote.register(api, Map.of("host", "10.9.8.7", "port", 9090));
    remote.publish(api, "yard");

    String body = getPage("/services/" + RemoteWorkstation.NAME + "/" + api).getBody();

    assertThat(body)
        .contains("Yard Scope")
        .contains(api + "." + RemoteWorkstation.NAME + ".lvh.test");
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
  void remoteServiceShowsTheSameCardsAndAlertsWithoutChangeButtons() {
    String api = newServiceName("api");
    remote.register(api, Map.of("host", "10.9.8.7", "port", 9090));
    remote.publish(api, "yard");

    String body = getPage("/services/" + RemoteWorkstation.NAME + "/" + api).getBody();

    assertThat(div(body, "role=\"alert\"")).contains("No Instance passes its checks");
    assertThat(div(body, "id=\"sections\"")).contains(">Publication</h2>");
    assertThat(div(body, "id=\"service\"")).doesNotContain("<form").doesNotContain("<button");
  }
}
