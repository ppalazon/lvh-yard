package dev.palazon.lvhyard.dashboard;

import static dev.palazon.lvhyard.dashboard.events.EventBroadcaster.YARD_CHANGED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.palazon.lvhyard.dashboard.events.EventDebouncer;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Consul watcher: Instance health, KV mutations, WAN pool changes, and SSE signals. */
class ConsulWatcherIT extends DashboardIT {

  @Autowired EventDebouncer debouncer;

  private static class EventStreamClient implements AutoCloseable {
    final HttpResponse<InputStream> response;
    final BlockingQueue<String> lines = new LinkedBlockingQueue<>();
    final InputStream stream;
    final Thread readerThread;

    EventStreamClient(int port) throws Exception {
      HttpClient client = HttpClient.newHttpClient();
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create("http://127.0.0.1:" + port + "/events"))
              .header("Accept", "text/event-stream")
              .GET()
              .build();
      this.response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
      this.stream = response.body();
      this.readerThread =
          Thread.ofVirtual()
              .name("test-consul-sse-reader")
              .start(() -> {
                try (BufferedReader reader =
                    new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                  String line;
                  while ((line = reader.readLine()) != null) {
                    lines.add(line);
                  }
                } catch (Exception ignored) {
                }
              });
    }

    @Override
    public void close() throws Exception {
      stream.close();
      readerThread.interrupt();
    }
  }

  @Test
  void wanPoolMembershipChangeEmitsYardChangedAndUpdatesCatalogAndServicePages() throws Exception {
    String service = newServiceName("watcher-wan");
    register(service, Map.of("host", "10.1.2.3", "port", 8080));

    // Verify Catalog and Service pages include HTMX SSE trigger for yard-changed
    String catalogPage = getPage("/").getBody();
    assertThat(openingTag(catalogPage, "id=\"catalog\""))
        .contains("hx-trigger=\"" + YARD_CHANGED + " from:body, every 30s\"");

    String servicePage = getPage("/services/ws-test/" + service).getBody();
    assertThat(openingTag(servicePage, "id=\"service\""))
        .contains("hx-trigger=\"" + YARD_CHANGED + " from:body, every 30s\"");

    try (EventStreamClient client = new EventStreamClient(port)) {
      // Trigger WAN pool membership change by force-leaving remote WAN member
      consulContainer.execInContainer("consul", "force-leave", "-wan", "ws-remote.ws-remote");

      await()
          .atMost(Duration.ofSeconds(15))
          .until(() -> client.lines.contains("event:" + YARD_CHANGED));
    } finally {
      // Reconnect WAN member so subsequent tests remain federated
      consulContainer.execInContainer(
          "consul", "join", "-wan", "consul-" + DashboardIT.WORKSTATION + ":8302");
    }
  }
}
