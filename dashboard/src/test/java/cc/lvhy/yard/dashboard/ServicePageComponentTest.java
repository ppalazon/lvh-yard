package cc.lvhy.yard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** A Service's detail page: its Instances and their health, its alerts and its refresh. */
class ServicePageComponentTest extends ServicePageComponentBase {

  /** The first Instance entry of {@code page}, up to its closing tag. */
  private static String instanceEntry(String page) {
    int at = page.indexOf("class=\"instance-entry\"");
    if (at < 0) {
      throw new AssertionError("No Instance entry");
    }
    return page.substring(at, page.indexOf("</li>", at));
  }

  @Test
  void showsTheServicesInstancesAndTheirHealth() {
    String api = newServiceName("api");
    register(api, Map.of("host", "10.1.2.3", "port", 8080));

    ResponseEntity<String> response = getPage(pagePath(api));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).contains("<h1>" + api + "</h1>");
    assertThat(instanceEntry(response.getBody()))
        .contains("10.1.2.3:8080")
        .contains(badge("destructive", "critical"));
  }

  @Test
  void removingTheLastInstanceStaysOnTheServicePage() {
    String web = newServiceName("web");
    String id = register(web, Map.of("host", "10.1.2.3", "port", 8080)).get("id").asString();

    ResponseEntity<String> response =
        postForm(pagePath(web) + "/instances/" + id + "/remove", Map.of());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER);
    assertThat(response.getHeaders().getLocation()).hasPath(pagePath(web));
    assertThat(get(servicePath(web)).getBody().get("empty").asBoolean()).isTrue();
  }

  @Test
  void unknownServiceIsNotFound() {
    ResponseEntity<String> response = getPage(pagePath(newServiceName("nothing")));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void unavailableServiceShowsARedAlertAndSaysItHasNoSources() {
    String web = newServiceName("web");
    String id = register(web, Map.of("host", "10.1.2.3", "port", 8080)).get("id").asString();
    publish(web);
    delete(instancesPath(web) + "/" + id);

    String page = getPage(pagePath(web)).getBody();

    assertThat(div(page, "role=\"alert\"")).contains("No Instances are registered");
    assertThat(div(page, "id=\"sources\""))
        .contains("No Instance Sources yet.")
        .doesNotContain("<table");
  }

  @Test
  void unhealthyServiceShowsARedAlert() {
    String web = newServiceName("web");
    register(web, Map.of("host", "10.1.2.3", "port", 8080));
    publish(web);

    String page = getPage(pagePath(web)).getBody();

    assertThat(div(page, "role=\"alert\"")).contains("No Instance passes its checks");
  }

  @Test
  void showsEachChecksLastOutput() {
    String api = newServiceName("api");
    int port = newLanPort();
    answerHttpOnLanAddress(port, "503 Service Unavailable", "warming up");
    register(api, Map.of("port", port, "check", Map.of("path", "/health", "interval", "1s")));

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() ->
            assertThat(instanceEntry(getPage(pagePath(api)).getBody()))
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

    assertThat(instanceEntry(getPage(pagePath(api)).getBody()))
        .contains(badge("outline", "backend"))
        .contains("<dt>owner</dt><dd>pablo</dd>");
  }

  @Test
  void refreshesTheServiceWithYardChangedSignalAndFallbackPoll() {
    String web = newServiceName("web");
    register(web, Map.of("host", "10.1.2.3", "port", 8080));

    String region = openingTag(getPage(pagePath(web)).getBody(), "id=\"service\"");

    assertThat(region)
        .contains("hx-get=\"" + pagePath(web) + "\"")
        .contains("hx-trigger=\"yard-changed from:body, every 30s\"")
        .contains("hx-select=\"#service\"")
        .contains("hx-swap=\"outerHTML\"")
        .as(
            "an error answer, such as while its Workstation is unreachable, leaves the page as it"
                + " is")
        .contains("hx-status:4xx=\"swap:none\"")
        .contains("hx-status:5xx=\"swap:none\"");
  }
}
