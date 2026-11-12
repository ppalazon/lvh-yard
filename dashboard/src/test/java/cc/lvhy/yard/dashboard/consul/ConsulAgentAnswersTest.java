package cc.lvhy.yard.dashboard.consul;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpServerErrorException;

/** What the agent says when a datacenter cannot be reached, as real HTTP answers. */
class ConsulAgentAnswersTest {

  private HttpServer server;

  private ConsulAgent agent(int status, String body) throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=utf-8");
          exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
          if (bytes.length > 0) {
            exchange.getResponseBody().write(bytes);
          }
          exchange.close();
        });
    server.start();
    return new ConsulAgent(
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
  void datacenterWithoutAPathIsAServerError() throws IOException {
    ConsulAgent agent =
        agent(500, "rpc error getting client: failed to get conn: no route to host");

    assertThatThrownBy(() -> agent.values("nostromo", "lvh-yard/services/"))
        .isInstanceOf(HttpServerErrorException.class);
  }

  @Test
  void missingPrefixHasNoValues() throws IOException {
    assertThat(agent(404, "").values("nostromo", "lvh-yard/services/")).isEmpty();
  }
}
