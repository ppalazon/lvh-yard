package cc.lvhy.yard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.json.JsonMapper;

/** The Route Diagram page: what it says for a Service on a Workstation with HTTP only. */
class RouteDiagramComponentTest extends DashboardComponentTest {

  private static final JsonMapper JSON = new JsonMapper();

  @AfterEach
  void removeProfiles() throws Exception {
    removeAllRouteProfiles();
    remote.removeAllRouteProfiles();
  }

  private static String diagramPath(String home, String service) {
    return "/services/" + home + "/" + service + "/diagram";
  }

  /** A Static Instance that listens, so its check passes. */
  private void registerPassing(String service) {
    int port = newLanPort();
    listenOnLanAddress(port);
    register(service, Map.of("port", port, "check", Map.of("interval", "1s")));
  }

  @Test
  void theServicePageLinksToIt() {
    String web = newServiceName("web");
    register(web, Map.of("host", "10.1.2.3", "port", 8080));

    String page = getPage("/services/ws-test/" + web).getBody();

    assertThat(page)
        .contains("href=\"" + diagramPath("ws-test", web) + "\"")
        .contains("Route Diagram");
  }

  /** An unclosed title row once pulled the hostname and facts up beside the title. */
  @Test
  void theButtonsLeaveTheTitleRowBeforeTheFactsStart() {
    String web = newServiceName("web");
    register(web, Map.of("host", "10.1.2.3", "port", 8080));

    String page = getPage("/services/ws-test/" + web).getBody();

    int buttons = page.indexOf("class=\"ml-auto flex flex-wrap gap-2\"");
    int facts = page.indexOf("class=\"grid items-start gap-4 lg:grid-cols-2\"", buttons);
    assertThat(page.substring(buttons, facts)).endsWith("</div></div><div ");
  }

  @Test
  void aPublishedServiceHasPlainHttpAndNoHttpsOnAnHttpOnlyWorkstation() {
    String web = newServiceName("web");
    registerPassing(web);
    put(publicationPath(web), Map.of("scope", "local"));
    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(() ->
            assertThat(getPage(diagramPath("ws-test", web)).getBody())
                .contains(web + ".lvh.test")
                .contains("plain HTTP")
                .contains("HTTPS is off")
                .contains("in the Route")
                .contains("passing"));
  }

  @Test
  void anUnpublishedServiceHasNoRoute() {
    String web = newServiceName("web");
    register(web, Map.of("host", "10.1.2.3", "port", 8080));

    String page = getPage(diagramPath("ws-test", web)).getBody();

    assertThat(page)
        .contains("is not published, so it has no hostname and no Route")
        .contains("10.1.2.3:8080");
  }

  @Test
  void aSourceBehindTheOneInUseIsWaiting() {
    String web = newServiceName("web");
    registerPassing(web);
    post(instancesPath(web), Map.of("host", "10.1.2.3", "port", 8080));
    put(publicationPath(web), Map.of("scope", "local"));
    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(() ->
            assertThat(getPage(diagramPath("ws-test", web)).getBody())
                .contains("waiting")
                .contains("in the Route")
                .contains("Round robin over the passing Instances of Static Instance"));
  }

  @Test
  void showsOptionValuesAndHidesTheUsersOfBasicAuth() throws Exception {
    putRouteProfiles(
        JSON.readTree(
            """
            {"diagram-login": {"basicAuth": {"users": ["ana:$2y$05$SECRETHASHSECRETHASH"],
                                              "removeHeader": true}},
             "diagram-team": {"headers": {"customRequestHeaders": {"X-Team": "platform"}}}}
            """));
    String web = newServiceName("web");
    registerPassing(web);
    put(
        publicationPath(web),
        Map.of("scope", "local", "profiles", List.of("diagram-login", "diagram-team")));

    String page = getPage(diagramPath("ws-test", web)).getBody();

    assertThat(page)
        .contains("diagram-login")
        .contains("basicAuth")
        .contains("asks who the user is")
        .contains("removeHeader")
        .contains("customRequestHeaders.X-Team")
        .contains("platform")
        .contains("(value hidden)")
        .doesNotContain("SECRETHASH");
  }

  @Test
  void aClientCertificateProfileGivesNoRouteWhereHttpsIsOff() throws Exception {
    putRouteProfiles(JSON.readTree("{\"diagram-cert\": {\"clientCertificate\": {}}}"));
    String web = newServiceName("web");
    registerPassing(web);
    put(publicationPath(web), Map.of("scope", "local", "profiles", List.of("diagram-cert")));

    assertThat(getPage(diagramPath("ws-test", web)).getBody())
        .contains("A Route Profile needs a Client Certificate");
  }

  @Test
  void aRemoteServiceShowsItsHomeWorkstationAndSkipsAnInstanceOnLoopback() {
    String api = newServiceName("api");
    remote.register(api, Map.of("host", "127.0.0.1", "port", 9091));
    remote.register(api, Map.of("host", "10.9.8.7", "port", 9092));
    remote.publish(api, "yard");

    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(() ->
            assertThat(getPage(diagramPath(RemoteWorkstation.NAME, api)).getBody())
                .contains("Workstation " + RemoteWorkstation.NAME)
                .contains("Across the LAN")
                .contains("skipped here")
                .contains("127.0.0.1:9091")
                .contains("10.9.8.7:9092"));
  }

  @Test
  void anUnknownServiceIsNotFound() {
    ResponseEntity<String> response = getPage(diagramPath("ws-test", newServiceName("missing")));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }
}
