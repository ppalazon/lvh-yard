package cc.lvhy.yard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** The Publication card of a Service's page: its switches, its Scope and its risk alerts. */
class ServicePublicationPageComponentTest extends ServicePageComponentBase {

  /** The risk alerts, each up to its own closing tags. */
  private static String risks(String page) {
    Matcher found =
        Pattern.compile(
                "<div class=\"alert\" id=\"alert-risk-\\d+\".*?</details></div>", Pattern.DOTALL)
            .matcher(page);
    StringBuilder risks = new StringBuilder();
    while (found.find()) {
      risks.append(found.group());
    }
    if (risks.isEmpty()) {
      throw new AssertionError("No risk alert");
    }
    return risks.toString();
  }

  /** The switch forms of the Publication card. */
  private static List<String> switches(String publication) {
    return Arrays.stream(publication.split("(?=<form)"))
        .filter(form -> form.contains("role=\"switch\""))
        .toList();
  }

  /** The one switch form of {@code publication} that has {@code part}. */
  private static String switchWith(String publication, String part) {
    List<String> found = switches(publication).stream().filter(s -> s.contains(part)).toList();
    assertThat(found).as("the switch with " + part).hasSize(1);
    return found.get(0);
  }

  private static boolean isOn(String switchForm) {
    return openingTag(switchForm, "role=\"switch\"").contains("checked");
  }

  @Test
  void publishingShowsTheHostnameAndTheState() {
    String web = newServiceName("web");
    register(web, Map.of("host", "10.1.2.3", "port", 8080));
    String page = pagePath(web);
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

    String page = getPage(pagePath(web)).getBody();

    assertThat(openingTag(page, "href=\"http://" + web + ".lvh.test"))
        .contains("target=\"_blank\"")
        .contains("rel=\"noopener noreferrer\"");
  }

  @Test
  void scopeCanBeChangedToYardAndBack() {
    String web = newServiceName("web");
    register(web, Map.of("host", "10.1.2.3", "port", 8080));
    publish(web);
    String page = pagePath(web);
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
    String page = pagePath(web);
    assertThat(getPage(page).getBody())
        .contains("Show in the access log")
        .contains("name=\"accessLog\" value=\"true\"")
        .doesNotContain("lvh-yard ws compose logs traefik");

    ResponseEntity<String> response = postForm(page + "/publish", Map.of("accessLog", "true"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER);
    assertThat(getPage(page).getBody())
        .contains("name=\"accessLog\" value=\"false\"")
        .contains("Traefik logs each request. To see the log, run:")
        .contains("lvh-yard ws compose logs traefik</code>");

    postForm(page + "/publish", Map.of("accessLog", "false"));

    assertThat(get(servicePath(web)).getBody().get("publication").get("accessLog").asBoolean())
        .isFalse();
  }

  @Test
  void mutualTlsCanBeTurnedOnAndOff() {
    String web = newServiceName("web");
    register(web, Map.of("host", "10.1.2.3", "port", 8080));
    publish(web);
    String page = pagePath(web);
    assertThat(getPage(page).getBody())
        .contains("Mutual TLS to the Instances")
        .contains("name=\"mutualTls\" value=\"true\"");

    ResponseEntity<String> response = postForm(page + "/publish", Map.of("mutualTls", "true"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER);
    assertThat(getPage(page).getBody())
        .contains("name=\"mutualTls\" value=\"false\"")
        .contains("that each one has a certificate for " + web + ".");
    assertThat(get(servicePath(web)).getBody().get("publication").get("mutualTls").asBoolean())
        .isTrue();

    postForm(page + "/publish", Map.of("mutualTls", "false"));

    assertThat(get(servicePath(web)).getBody().get("publication").get("mutualTls").asBoolean())
        .isFalse();
  }

  @Test
  void publicationSwitchesShowTheirStateAndPostTheOtherValue() {
    String web = newServiceName("web");
    register(web, Map.of("host", "10.1.2.3", "port", 8080));
    String page = pagePath(web);

    String unpublished = div(getPage(page).getBody(), "id=\"publication\"");

    assertThat(switches(unpublished)).hasSize(1);
    assertThat(isOn(switches(unpublished).get(0))).isFalse();

    publish(web);
    put(publicationPath(web), Map.of("scope", "yard"));
    String published = div(getPage(page).getBody(), "id=\"publication\"");

    assertThat(switches(published)).hasSize(4);
    // Published and Share with the Yard are on. The access log and mutual TLS are off.
    assertThat(isOn(switchWith(published, "action=\"" + page + "/unpublish\""))).isTrue();
    String scope = switchWith(published, "name=\"scope\"");
    assertThat(isOn(scope)).isTrue();
    assertThat(scope).contains("name=\"scope\" value=\"local\"");
    String accessLog = switchWith(published, "name=\"accessLog\"");
    assertThat(isOn(accessLog)).isFalse();
    assertThat(accessLog).contains("name=\"accessLog\" value=\"true\"");
    String mutualTls = switchWith(published, "name=\"mutualTls\"");
    assertThat(isOn(mutualTls)).isFalse();
    assertThat(mutualTls).contains("name=\"mutualTls\" value=\"true\"");
  }

  @Test
  void unknownScopeIsRefusedAndKeepsTheScope() {
    String web = newServiceName("web");
    register(web, Map.of("host", "10.1.2.3", "port", 8080));
    publish(web);

    ResponseEntity<String> response =
        postForm(pagePath(web) + "/publish", Map.of("scope", "world"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(get(servicePath(web)).getBody().get("publication").get("scope").asString())
        .isEqualTo("local");
  }

  @Test
  void yardScopedServiceWarnsAboutEachInstanceOnLoopbackWithAClosedNotARedAlert() {
    String web = newServiceName("web");
    register(web, Map.of("host", "127.0.0.1", "port", 8080));
    register(web, Map.of("host", "127.0.0.1", "port", 8081));
    assertThat(getPage(pagePath(web)).getBody()).doesNotContain("loopback");

    put(publicationPath(web), Map.of("scope", "yard"));

    String body = getPage(pagePath(web)).getBody();
    assertThat(risks(body).split("<h2>Loopback Instances</h2>", -1)).hasSize(3);
    assertThat(risks(body))
        .contains("127.0.0.1:8080 is on loopback")
        .contains("127.0.0.1:8081 is on loopback")
        .doesNotContain("open=\"\"");
    assertThat(div(body, "role=\"alert\""))
        .as("a risk is not a red alert")
        .doesNotContain("loopback");
    assertThat(body).as("its Home Workstation reaches it").doesNotContain("Out of range");
  }

  @Test
  void unpublishingKeepsTheInstances() {
    String web = newServiceName("web");
    register(web, Map.of("host", "10.1.2.3", "port", 8080));
    publish(web);
    String page = pagePath(web);

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
    assertThat(getPage(pagePath(web)).getBody()).contains("Unavailable");

    ResponseEntity<String> response = postForm(pagePath(web) + "/unpublish", Map.of());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER);
    assertThat(response.getHeaders().getLocation()).hasPath(pagePath(web));
    assertThat(get(servicePath(web)).getBody().get("state").asString()).isEqualTo("unpublished");
  }

  @Test
  void publishingAnUnknownServiceIsNotFound() {
    ResponseEntity<String> response =
        postForm(pagePath(newServiceName("nothing")) + "/publish", Map.of());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void unpublishingAnUnknownServiceIsNotFound() {
    ResponseEntity<String> response =
        postForm(pagePath(newServiceName("nothing")) + "/unpublish", Map.of());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }
}
