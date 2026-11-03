package dev.palazon.lvhyard.dashboard.status;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import dev.palazon.lvhyard.dashboard.consul.Consul;
import dev.palazon.lvhyard.dashboard.consul.ConsulSettings;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The Consul part, read from a stand-in for Consul's HTTP API that answers what a real Consul would
 * in states the test Yard cannot reach, such as no leader.
 */
class ConsulStatusTest {

  private static final String WS_B = memberJson("ws-b", "10.0.0.2", 8302);

  private HttpServer server;

  @AfterEach
  void stop() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void consulWithNoLeaderIsFailingButStillShowsItsWanPool() throws IOException {
    Part consul = read(consulAnswering("\"\"", WS_B), "");

    assertThat(consul.state()).isEqualTo(PartState.FAILING);
    assertThat(consul.reason()).contains("no leader");
    assertThat(consul.details())
        .contains(new Part.Detail("ws-b", "alive at 10.0.0.2:8302", PartState.OK));
  }

  @Test
  void aBracketedIpv6PeerInTheWanPoolIsNotMissing() throws IOException {
    Part consul =
        read(
            consulAnswering("\"10.0.0.1:8300\"", WS_B, memberJson("ws-c", "fd00::1", 8302)),
            "[fd00::1]:8302 [fd00::1] fd00::1");

    assertThat(consul.state()).isEqualTo(PartState.OK);
    assertThat(consul.details()).noneMatch(detail -> detail.name().startsWith("peer "));
  }

  @Test
  void aPeerWhoseAddressLookupIsSlowIsAWarningOfItsOwn() throws IOException {
    ConsulStatus status =
        new ConsulStatus(
            consul(consulAnswering("\"10.0.0.1:8300\"", WS_B)),
            settings(URI.create("http://unused"), "slow.example 10.0.0.2"),
            host -> {
              if (host.equals("slow.example")) {
                try {
                  Thread.sleep(Duration.ofSeconds(10));
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                }
              }
              return InetAddress.getAllByName(host);
            });

    Instant started = Instant.now();
    Part consul = status.read();

    assertThat(Duration.between(started, Instant.now())).isLessThan(StatusReader.PART_TIMEOUT);
    assertThat(consul.state()).isEqualTo(PartState.WARNING);
    Part.Detail slow =
        consul.details().stream()
            .filter(detail -> detail.name().equals("peer slow.example"))
            .findFirst()
            .orElseThrow();
    assertThat(slow.state()).isEqualTo(PartState.WARNING);
    assertThat(slow.value()).contains("lookup");
    assertThat(consul.details()).noneMatch(detail -> detail.name().equals("peer 10.0.0.2"));
  }

  private Part read(URI consulUrl, String peers) {
    return new ConsulStatus(consul(consulUrl), settings(consulUrl, peers)).read();
  }

  private static Consul consul(URI url) {
    return new Consul(settings(url, ""));
  }

  private static ConsulSettings settings(URI url, String peers) {
    return new ConsulSettings(url, peers, Duration.ofSeconds(3), Duration.ofSeconds(10));
  }

  /** A stand-in Consul whose leader is {@code leaderJson} and whose WAN pool is {@code members}. */
  private URI consulAnswering(String leaderJson, String... members) throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    Map<String, String> answers =
        Map.of(
            "/v1/status/leader",
            leaderJson,
            "/v1/agent/members",
            "[" + String.join(",", members) + "]");
    server.createContext(
        "/",
        exchange -> {
          String answer = answers.get(exchange.getRequestURI().getPath());
          byte[] body = (answer == null ? "" : answer).getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(answer == null ? 404 : 200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
    server.start();
    return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
  }

  private static String memberJson(String datacenter, String address, int port) {
    return """
    {"Name":"%s.%s","Addr":"%s","Port":%d,"Status":1,"Tags":{"dc":"%s"}}\
    """
        .formatted(datacenter, datacenter, address, port, datacenter);
  }
}
