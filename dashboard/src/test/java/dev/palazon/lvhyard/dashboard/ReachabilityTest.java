package dev.palazon.lvhyard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.GenericContainer;
import tools.jackson.databind.JsonNode;

/**
 * A Reachability test of one Service, from this Workstation: straight to each Instance, and through
 * a real Traefik that reads its Routes from the dashboard under test.
 */
class ReachabilityTest extends DashboardTest {

  private static GenericContainer<?> traefik;

  /** The HTTP servers this test started, stopped after it. */
  private final List<HttpServer> servers = new ArrayList<>();

  @BeforeEach
  void startTraefik() {
    if (traefik == null) {
      traefik = TestcontainersConfiguration.traefik(port);
      traefik.start();
    }
  }

  @AfterEach
  void stopServers() {
    servers.forEach(server -> server.stop(0));
  }

  @AfterAll
  static void stopTraefik() {
    if (traefik != null) {
      traefik.stop();
      traefik = null;
    }
  }

  @Test
  void publishedServiceIsReachedThroughTraefikAtItsHostname() {
    String web = newServiceName("web");
    int listening = answerHttpOnLoopback("hello from " + web);
    register(web, Map.of("host", "127.0.0.1", "port", listening));
    publish(web);

    JsonNode result =
        await()
            .atMost(Duration.ofSeconds(15))
            .until(
                () -> get(reachabilityPath(WORKSTATION, web)).getBody(),
                tested -> tested.get("passed").asBoolean());

    assertThat(result.get("instances").get(0).get("reached").asBoolean()).isTrue();
    assertThat(result.get("routes")).hasSize(1);
    JsonNode route = result.get("routes").get(0);
    assertThat(route.get("hostname").asString()).isEqualTo(web + ".lvh.test");
    assertThat(route.get("reached").asBoolean()).isTrue();
    assertThat(route.get("status").asInt()).isEqualTo(200);
    assertThat(result.has("noRoute")).isFalse();
  }

  @Test
  void serviceThatStreamsItsAnswerIsReachedOnceItsHeadersCome() throws IOException {
    String events = newServiceName("events");
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          exchange.sendResponseHeaders(200, 0);
          exchange.getResponseBody().write("data: hello\n\n".getBytes(StandardCharsets.UTF_8));
          exchange.getResponseBody().flush();
          try {
            Thread.sleep(Duration.ofSeconds(30));
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          exchange.close();
        });
    server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
    server.start();
    servers.add(server);
    register(events, Map.of("host", "127.0.0.1", "port", server.getAddress().getPort()));
    publish(events);

    JsonNode result =
        await()
            .atMost(Duration.ofSeconds(15))
            .until(
                () -> get(reachabilityPath(WORKSTATION, events)).getBody(),
                tested -> tested.get("routes").get(0).path("status").asInt() != 404);

    assertThat(result.get("routes").get(0).get("reached").asBoolean()).isTrue();
    assertThat(result.get("routes").get(0).get("status").asInt()).isEqualTo(200);
  }

  @Test
  void routeToAnInstanceThatDoesNotListenFailsWithTraefiksAnswer() {
    String web = newServiceName("web");
    int closed = answerHttpOnLoopback("soon gone");
    servers.removeLast().stop(0);
    register(web, Map.of("host", "127.0.0.1", "port", closed));
    publish(web);

    JsonNode result =
        await()
            .atMost(Duration.ofSeconds(15))
            .until(
                () -> get(reachabilityPath(WORKSTATION, web)).getBody(),
                tested -> tested.get("routes").get(0).path("status").asInt() == 502);

    JsonNode route = result.get("routes").get(0);
    assertThat(route.get("reached").asBoolean()).isFalse();
    assertThat(route.get("error").asString()).contains("502");
    assertThat(result.get("passed").asBoolean()).isFalse();
  }

  @Test
  void unpublishedServiceIsTestedStraightToEachInstanceAndHasNoRoute() {
    String api = newServiceName("api");
    listenOnLanAddress(7101);
    register(api, Map.of("port", 7101));
    register(api, Map.of("port", 7102));

    ResponseEntity<JsonNode> response = get(reachabilityPath(WORKSTATION, api));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode result = response.getBody();
    assertThat(result.get("instances")).hasSize(2);
    JsonNode listening = result.get("instances").get(0);
    assertThat(listening.get("address").asString()).isEqualTo(lanAddress() + ":7101");
    assertThat(listening.get("reached").asBoolean()).isTrue();
    assertThat(listening.has("error")).isFalse();
    JsonNode closed = result.get("instances").get(1);
    assertThat(closed.get("address").asString()).isEqualTo(lanAddress() + ":7102");
    assertThat(closed.get("reached").asBoolean()).isFalse();
    assertThat(closed.get("error").asString()).containsIgnoringCase("refused");
    assertThat(result.get("routes")).isEmpty();
    assertThat(result.get("noRoute").asString()).contains("Not published");
    assertThat(result.get("passed").asBoolean()).isFalse();
  }

  @Test
  void remoteServiceWithLocalScopeIsTestedStraightToItsInstancesAndSkipsOneOutOfRange() {
    String api = newServiceName("api");
    remote.register(api, Map.of("host", "127.0.0.1", "port", 9090));
    remote.publish(api, "local");

    JsonNode result = get(reachabilityPath(RemoteWorkstation.NAME, api)).getBody();

    assertThat(result.get("workstation").asString()).isEqualTo(RemoteWorkstation.NAME);
    JsonNode instance = result.get("instances").get(0);
    assertThat(instance.get("outOfRange").asBoolean()).isTrue();
    assertThat(instance.get("reached").asBoolean()).isFalse();
    assertThat(instance.has("error")).isFalse();
    assertThat(result.get("routes")).isEmpty();
    assertThat(result.get("noRoute").asString())
        .contains("Local Scope on " + RemoteWorkstation.NAME);
    assertThat(result.get("passed").asBoolean()).as("a skipped Instance is no failure").isTrue();
  }

  @Test
  void remoteServiceWithYardScopeButNoInstanceInRangeHasNoRoute() {
    String api = newServiceName("api");
    remote.register(api, Map.of("host", "127.0.0.1", "port", 9090));
    remote.publish(api, "yard");

    JsonNode result = get(reachabilityPath(RemoteWorkstation.NAME, api)).getBody();

    assertThat(result.get("routes")).isEmpty();
    assertThat(result.get("noRoute").asString()).contains("No Instance in range");
  }

  @Test
  void remoteServiceWithYardScopeIsRequestedAtItsHostnameAndEachActiveAlias() {
    String grafana = newServiceName("grafana");
    // TEST-NET-1, which no computer answers at.
    remote.register(grafana, Map.of("host", "192.0.2.10", "port", 3000));
    remote.publish(grafana, "yard");
    String alias = newServiceName("graf");
    put(
        AliasesTest.aliasPath(alias),
        Map.of("workstation", RemoteWorkstation.NAME, "service", grafana));

    JsonNode result = get(reachabilityPath(RemoteWorkstation.NAME, grafana)).getBody();

    assertThat(result.get("routes").valueStream().map(route -> route.get("hostname").asString()))
        .containsExactly(grafana + "." + RemoteWorkstation.NAME + ".lvh.test", alias + ".lvh.test");
    assertThat(result.get("routes").valueStream().map(route -> route.get("reached").asBoolean()))
        .containsOnly(false);
    assertThat(result.get("instances").get(0).get("reached").asBoolean()).isFalse();
    assertThat(result.get("passed").asBoolean()).isFalse();
  }

  @Test
  void servicePageHasATestButtonThatShowsTheResults() {
    String api = newServiceName("api");
    listenOnLanAddress(7103);
    register(api, Map.of("port", 7103));
    register(api, Map.of("port", 7104));
    String page = "/services/ws-test/" + api;
    assertThat(getPage(page).getBody())
        .contains("action=\"" + page + "/reachability\"")
        .doesNotContain("Connection refused");

    ResponseEntity<String> response = getPage(page + "/reachability");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody())
        .contains("<h1>" + api + "</h1>")
        .contains("Reachability from ws-test")
        .contains(lanAddress() + ":7103")
        .contains("Reached")
        .contains(lanAddress() + ":7104")
        .containsIgnoringCase("refused")
        .contains("Not published, so only its Instances are tested");
  }

  @Test
  void remoteServicePageHasATestButtonToo() {
    String api = newServiceName("api");
    remote.register(api, Map.of("host", "127.0.0.1", "port", 9090));
    String page = "/services/" + RemoteWorkstation.NAME + "/" + api;

    assertThat(getPage(page).getBody()).contains("action=\"" + page + "/reachability\"");
    assertThat(getPage(page + "/reachability").getBody())
        .contains("Out of range")
        .contains("Only " + RemoteWorkstation.NAME + " reaches this address")
        .doesNotContain("Not reached");
  }

  @Test
  void ownServiceIsTestedWithoutNamingThisWorkstation() {
    String api = newServiceName("api");
    register(api, Map.of("host", "10.1.2.3", "port", 7105));

    ResponseEntity<JsonNode> response = get(servicePath(api) + "/reachability");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().get("workstation").asString()).isEqualTo(WORKSTATION);
    assertThat(response.getBody().get("instances").get(0).get("address").asString())
        .isEqualTo("10.1.2.3:7105");
    assertThat(get(servicePath(newServiceName("nothing")) + "/reachability").getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void unknownServiceIsNotFound() {
    ResponseEntity<JsonNode> response =
        get(reachabilityPath(WORKSTATION, newServiceName("nothing")));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  /**
   * Starts an HTTP server on a free loopback port that answers every request with 200 and {@code
   * body}, as a development server on this Workstation would, and answers its port. It is stopped
   * after the test.
   */
  private int answerHttpOnLoopback(String body) {
    try {
      HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext(
          "/",
          exchange -> {
            byte[] answer = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, answer.length);
            exchange.getResponseBody().write(answer);
            exchange.close();
          });
      server.start();
      servers.add(server);
      return server.getAddress().getPort();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static String reachabilityPath(String workstation, String service) {
    return "/api/services/" + workstation + "/" + service + "/reachability";
  }
}
