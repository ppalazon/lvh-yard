package cc.lvhy.yard.dashboard.consul;

import static org.assertj.core.api.Assertions.assertThat;

import cc.lvhy.yard.dashboard.consul.Consul.AgentService;
import cc.lvhy.yard.dashboard.consul.Consul.Check;
import cc.lvhy.yard.dashboard.consul.Consul.CheckStatus;
import cc.lvhy.yard.dashboard.consul.Consul.HealthEntry;
import cc.lvhy.yard.dashboard.consul.Consul.Registration;
import cc.lvhy.yard.dashboard.consul.Consul.SerfStatus;
import cc.lvhy.yard.dashboard.consul.Consul.WanMember;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The JSON that {@link ConsulAgent} reads and writes, with Consul's own field names, as answers of
 * a stand-in HTTP server. {@code ConsulAgentIT} checks the same against a real Consul.
 */
class ConsulAgentWireTest {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private HttpServer server;

  private ConsulAgent agent;

  /** The body of the last PUT. */
  private final AtomicReference<String> sent = new AtomicReference<>();

  @BeforeEach
  void start() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    answer(
        "/v1/agent/members",
        """
        [{"Name":"ws-a.ws-a","Addr":"10.0.0.1","Port":8302,"Tags":{"dc":"ws-a","role":"consul"},
          "Status":1,"ProtocolMin":1},
         {"Name":"ws-b.ws-b","Addr":"10.0.0.2","Port":8302,"Tags":{"dc":"ws-b"},"Status":4}]
        """);
    answer(
        "/v1/agent/services",
        """
        {"web:1":{"ID":"web:1","Service":"web","Tags":["blue"],"Meta":{"owner":"me"},"Port":80,
          "Address":"10.0.0.2","Weights":{"Passing":1,"Warning":1},"EnableTagOverride":false},
         "bare":{"ID":"bare","Service":"bare","Tags":null,"Meta":null,"Address":""}}
        """);
    answer(
        "/v1/agent/service/web:1",
        """
        {"ID":"web:1","Service":"web","Tags":["blue"],"Meta":{"owner":"me"},"Port":80,
         "Address":"10.0.0.2"}
        """);
    answer(
        "/v1/health/service/web",
        """
        [{"Node":{"Node":"n1","Address":"10.0.0.2"},
          "Service":{"ID":"web:1","Service":"web","Tags":["blue"],"Port":80,"Address":"10.0.0.2",
                     "Meta":null},
          "Checks":[
            {"Node":"n1","CheckID":"serfHealth","Name":"Serf Health Status","Status":"passing",
             "Output":"Agent alive and reachable","ServiceID":"","ServiceName":"","Type":""},
            {"Node":"n1","CheckID":"service:web:1","Name":"TCP 10.0.0.2:80","Status":"critical",
             "Output":"connection refused","ServiceID":"web:1","ServiceName":"web","Type":"tcp",
             "Interval":"10s","Timeout":"2s"}]}]
        """);
    answer("/v1/agent/self", "{\"Member\":{\"Name\":\"n1\",\"Addr\":\"10.20.0.1\",\"Port\":8301}}");
    answer("/v1/agent/service/register", "");
    server.start();
    agent =
        new ConsulAgent(
            new ConsulSettings(
                URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                null,
                Duration.ofSeconds(3),
                Duration.ofSeconds(10)));
  }

  @AfterEach
  void stop() {
    server.stop(0);
  }

  @Test
  void wanMembersCarryTheirAddressPortStatusAndDatacenter() {
    List<WanMember> members = agent.wanMembers();

    assertThat(members)
        .containsExactly(
            new WanMember(
                "10.0.0.1", 8302, SerfStatus.ALIVE, Map.of("dc", "ws-a", "role", "consul")),
            new WanMember("10.0.0.2", 8302, SerfStatus.FAILED, Map.of("dc", "ws-b")));
    assertThat(members.get(0).datacenter()).isEqualTo("ws-a");
    assertThat(members.get(0).alive()).isTrue();
    assertThat(members.get(1).alive()).isFalse();
  }

  @Test
  void agentServicesCarryTheirSettingsAndFillWhatConsulLeavesOut() {
    Map<String, AgentService> services = agent.agentServices();

    assertThat(services.get("web:1"))
        .isEqualTo(
            new AgentService(
                "web:1", "web", "10.0.0.2", 80, List.of("blue"), Map.of("owner", "me")));
    assertThat(services.get("bare"))
        .isEqualTo(new AgentService("bare", "bare", "", 0, List.of(), Map.of()));
  }

  @Test
  void oneAgentServiceIsReadByItsIdAndAMissingOneIsEmpty() {
    assertThat(agent.agentService("web:1"))
        .contains(
            new AgentService(
                "web:1", "web", "10.0.0.2", 80, List.of("blue"), Map.of("owner", "me")));
    assertThat(agent.agentService("nothing")).isEmpty();
  }

  @Test
  void healthEntriesCarryTheServiceAndEachCheck() {
    List<HealthEntry> entries = agent.health("web");

    assertThat(entries).hasSize(1);
    assertThat(entries.get(0).service())
        .isEqualTo(new AgentService("web:1", "web", "10.0.0.2", 80, List.of("blue"), Map.of()));
    assertThat(entries.get(0).checks())
        .containsExactly(
            new CheckStatus(
                "Serf Health Status", "passing", "Agent alive and reachable", "", "", null, null),
            new CheckStatus(
                "TCP 10.0.0.2:80", "critical", "connection refused", "web:1", "tcp", "10s", "2s"));
  }

  @Test
  void theAgentAddressIsTheAddressOfItsMember() {
    assertThat(agent.agentAddress()).isEqualTo("10.20.0.1");
  }

  @Test
  void aRegistrationIsSentWithConsulsFieldNamesAndNoEmptyCheckFields() {
    agent.register(
        new Registration(
            "web:1",
            "web",
            "10.0.0.2",
            80,
            List.of("blue"),
            Map.of("owner", "me"),
            new Check("TCP 10.0.0.2:80", null, "10.0.0.2:80", "10s", "2s")));

    assertThat(sentJson())
        .isEqualTo(
            json(
                """
                {"ID":"web:1","Name":"web","Address":"10.0.0.2","Port":80,"Tags":["blue"],
                 "Meta":{"owner":"me"},
                 "Check":{"Name":"TCP 10.0.0.2:80","TCP":"10.0.0.2:80","Interval":"10s",
                          "Timeout":"2s"}}
                """));
  }

  @Test
  void anHttpCheckIsSentWithItsUrl() {
    agent.register(
        new Registration(
            "web:1",
            "web",
            "h",
            80,
            List.of(),
            Map.of(),
            new Check("HTTP GET /up", "http://h:80/up", null, "10s", "2s")));

    assertThat(sentJson().get("Check"))
        .isEqualTo(
            json(
                """
                {"Name":"HTTP GET /up","HTTP":"http://h:80/up","Interval":"10s","Timeout":"2s"}
                """));
  }

  private void answer(String path, String body) {
    server.createContext(
        path,
        exchange -> {
          if (exchange.getRequestMethod().equals("PUT")) {
            sent.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          }
          byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, bytes.length == 0 ? -1 : bytes.length);
          if (bytes.length > 0) {
            exchange.getResponseBody().write(bytes);
          }
          exchange.close();
        });
  }

  private JsonNode sentJson() {
    return json(sent.get());
  }

  private static JsonNode json(String text) {
    return JSON.readTree(text);
  }
}
