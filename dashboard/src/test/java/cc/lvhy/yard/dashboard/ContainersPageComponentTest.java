package cc.lvhy.yard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import cc.lvhy.yard.dashboard.docker.FakeDocker;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Browsing containers and creating a Service from one container or Compose service. */
class ContainersPageComponentTest extends DashboardComponentTest {
  private static final String ACTION = "/containers/services";

  @Test
  void runningAndStoppedReplicasOfferTheSameComposeTargetWithoutIndividualCreationLinks() {
    String project = newComposeProject();
    String running = newContainerName();
    String stopped = newContainerName();
    start(FakeDocker.container(running).compose(project, "web").publishedOn("127.0.0.1", 80));
    stopContainer(start(FakeDocker.container(stopped).compose(project, "web").port(80)));

    String page = decoded(getPage("/containers").getBody());
    int runningSection = page.indexOf(">Running<");
    int stoppedSection = page.indexOf(">Stopped<");
    assertThat(runningSection).isNotNegative().isLessThan(stoppedSection);
    assertThat(page.substring(runningSection, stoppedSection))
        .contains(createPath(project + "/web"));
    assertThat(page.substring(stoppedSection)).contains(createPath(project + "/web"));
    assertThat(page).doesNotContain(createPath(running)).doesNotContain(createPath(stopped));
    assertThat(page).doesNotContain("type=\"checkbox\"");
  }

  @Test
  void followedStandaloneAndComposeTargetsHideCreateLinksIncludingStoppedFollowedReplicas() {
    String project = newComposeProject();
    String running = newContainerName();
    String stopped = newContainerName();
    String plain = newContainerName();
    String unused = newContainerName();
    start(FakeDocker.container(running).compose(project, "web").port(80));
    stopContainer(start(FakeDocker.container(stopped).compose(project, "web").port(80)));
    start(FakeDocker.container(plain).port(80));
    start(FakeDocker.container(unused).port(80));
    String followed = newServiceName("followed");
    bind(followed, stopped, 80);
    bind(followed, plain, 80);

    String page = decoded(getPage("/containers").getBody());

    assertThat(page).doesNotContain(createPath(project + "/web")).doesNotContain(createPath(plain));
    assertThat(page)
        .contains(createPath(unused))
        .contains("href=\"/services/ws-test/" + followed + "\"");
  }

  @Test
  void composeFormUsesRunningSiblingPortsAndProposedServiceName() {
    String project = newComposeProject();
    start(FakeDocker.container(newContainerName()).compose(project, "Front_End").port(80));
    stopContainer(start(FakeDocker.container(newContainerName()).compose(project, "Front_End")));

    String page = getPage(createPath(project + "/Front_End")).getBody();

    assertThat(openingTag(page, "name=\"name\"")).contains("value=\"front-end\"");
    assertThat(openingTag(page, "name=\"containerPort\"")).contains("value=\"80\"");
  }

  @Test
  void comboboxListsEveryDetectedContainerPortOnceAcrossComposeReplicas() {
    String project = newComposeProject();
    start(
        FakeDocker.container(newContainerName()).compose(project, "web").published(80).port(9000));
    start(FakeDocker.container(newContainerName()).compose(project, "web").port(80).port(8080));

    String page = getPage(createPath(project + "/web")).getBody();

    assertThat(openingTag(page, "name=\"containerPort\"")).contains("role=\"combobox\"");
    assertThat(page).contains("data-value=\"80\"", "data-value=\"8080\"", "data-value=\"9000\"");
    assertThat(page.split("data-value=\"80\"", -1)).hasSize(2);
  }

  @Test
  void stoppedContainerNeedsAPortAndAcceptsATypedPort() throws Exception {
    String target = newContainerName();
    stopContainer(start(FakeDocker.container(target).port(80)));
    String service = newServiceName("stopped");
    String page = getPage(createPath(target)).getBody();
    assertThat(openingTag(page, "name=\"containerPort\"")).contains("value=\"\"");

    var invalid =
        submit(List.of(target), Map.of("name", service, "containerPort", "", "scope", "local"));
    assertThat(invalid.statusCode()).isEqualTo(400);
    assertThat(invalid.body()).contains("id=\"containerPort-error\"");
    assertThat(get(servicePath(service)).getStatusCode().value()).isEqualTo(404);

    var accepted =
        submit(List.of(target), Map.of("name", service, "containerPort", "6379", "scope", "local"));
    assertThat(accepted.statusCode()).isEqualTo(303);
    var stored = get(servicePath(service)).getBody();
    assertThat(stored.get("containerBindings")).hasSize(1);
    assertThat(stored.get("containerBindings").get(0).get("port").asInt()).isEqualTo(6379);
    assertThat(stored.get("instances")).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void repeatedTargetParametersAreRefusedBeforeCreationEvenForTheSameTarget(boolean same)
      throws Exception {
    String first = newContainerName();
    String second = same ? first : newContainerName();
    String service = newServiceName("invalid");
    var response =
        submit(
            List.of(first, second),
            Map.of("name", service, "containerPort", "80", "scope", "yard", "publish", "true"));
    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(get(servicePath(service)).getStatusCode().value()).isEqualTo(404);
    assertThat(getPage(createPath(first) + "&target=" + second).getStatusCode().value())
        .isEqualTo(400);
  }

  @Test
  void missingTargetCannotOpenOrSubmitTheForm() throws Exception {
    String service = newServiceName("missing");
    assertThat(getPage("/containers/services/new").getStatusCode().value()).isEqualTo(400);
    var response =
        submit(List.of(), Map.of("name", service, "containerPort", "80", "scope", "local"));
    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(get(servicePath(service)).getStatusCode().value()).isEqualTo(404);
  }

  @Test
  void existingNameIsRefusedWithoutBindingOrPublishingIt() throws Exception {
    String service = newServiceName("existing");
    create(service);
    var response =
        submit(
            List.of(newContainerName()),
            Map.of("name", service, "containerPort", "80", "scope", "local", "publish", "true"));
    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(response.body()).contains("id=\"name-error\"");
    var stored = get(servicePath(service)).getBody();
    assertThat(stored.get("containerBindings")).isEmpty();
    assertThat(stored.get("state").asString()).isEqualTo("unpublished");
  }

  @ParameterizedTest
  @CsvSource({"true,local,true", "true,yard,false", "false,local,true", "false,yard,false"})
  void createsExactlyOneBindingForTheTarget(boolean publish, String scope, boolean compose)
      throws Exception {
    String project = newComposeProject();
    String standalone = newContainerName();
    start(FakeDocker.container(newContainerName()).compose(project, "web").port(80));
    start(FakeDocker.container(newContainerName()).compose(project, "web").port(80));
    start(FakeDocker.container(standalone).port(9000));
    String service = newServiceName("single");
    Map<String, String> fields =
        new java.util.LinkedHashMap<>(
            Map.of("name", service, "containerPort", compose ? "80" : "9000", "scope", scope));
    if (publish) {
      fields.put("publish", "true");
    }

    var response = submit(List.of(compose ? project + "/web" : standalone), fields);

    assertThat(response.statusCode()).isEqualTo(303);
    assertThat(response.headers().firstValue("location")).hasValue("/services/ws-test/" + service);
    var stored = get(servicePath(service)).getBody();
    assertThat(stored.get("containerBindings")).hasSize(1);
    assertThat(stored.get("instances")).hasSize(compose ? 2 : 1);
    var binding = stored.get("containerBindings").get(0);
    assertThat(binding.get("port").asInt()).isEqualTo(compose ? 80 : 9000);
    if (compose) {
      assertThat(binding.get("composeProject").asString()).isEqualTo(project);
      assertThat(binding.get("composeService").asString()).isEqualTo("web");
    } else {
      assertThat(binding.get("container").asString()).isEqualTo(standalone);
    }
    if (publish) {
      assertThat(stored.get("publication").get("scope").asString()).isEqualTo(scope);
    } else {
      assertThat(stored.get("state").asString()).isEqualTo("unpublished");
    }
  }

  @Test
  void dockerFailureShowsItsReasonAndLeavesThePageAvailableForRecovery() {
    remote.whileDashboardRunsWith(
        () -> {
          String page = remote.page("/containers");
          assertThat(page).contains("No Docker socket at /nowhere/docker.sock");
        },
        "--lvh-yard.docker.host=unix:///nowhere/docker.sock");
  }

  private static String createPath(String target) {
    return "/containers/services/new?target=" + target;
  }

  private static String decoded(String value) {
    return URLDecoder.decode(value, StandardCharsets.UTF_8);
  }

  private HttpResponse<String> submit(List<String> targets, Map<String, String> fields)
      throws Exception {
    List<String> pairs = new ArrayList<>();
    targets.forEach(target -> pairs.add("target=" + encode(target)));
    fields.forEach((key, value) -> pairs.add(encode(key) + "=" + encode(value)));
    var request =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + ACTION))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(String.join("&", pairs)))
            .build();
    try (var client = HttpClient.newHttpClient()) {
      return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
