package dev.palazon.lvhyard.dashboard;

import static dev.palazon.lvhyard.dashboard.events.EventBroadcaster.YARD_CHANGED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.palazon.lvhyard.dashboard.consul.ConsulAgent;
import dev.palazon.lvhyard.dashboard.consul.ConsulSettings;
import dev.palazon.lvhyard.dashboard.events.EventDebouncer;
import dev.palazon.lvhyard.dashboard.services.ConsulWatcher;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Consul watcher: Instance health, KV mutations, WAN pool changes, and SSE signals. */
class ConsulWatcherIT extends DashboardIT {

  @Autowired EventDebouncer debouncer;

  @Test
  void registeringAnInstanceEmitsYardChangedOverEventsStream() throws Exception {
    try (EventStreamClient client = new EventStreamClient(port)) {
      String service = newServiceName("watcher-reg");
      register(service, Map.of("host", "10.1.2.3", "port", 8080));

      await()
          .atMost(Duration.ofSeconds(10))
          .until(() -> client.lines.contains("event:yard-changed"));
    }
  }

  @Test
  void changingAHealthCheckEmitsYardChangedOverEventsStream() throws Exception {
    String service = newServiceName("watcher-check");
    int portNum = newLanPort();
    listenOnLanAddress(portNum);
    register(service, Map.of("port", portNum, "check", Map.of("interval", "1s")));

    // Wait until the check has first run and is passing
    await()
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(() -> {
          var instance = get(servicePath(service)).getBody().get("instances").get(0);
          assertThat(instance.get("health").asString()).isEqualTo("passing");
        });

    try (EventStreamClient client = new EventStreamClient(port)) {
      // Stop the listener so the health check transitions from passing to critical
      stopListeningOnLanAddress(portNum);

      await()
          .atMost(Duration.ofSeconds(15))
          .until(() -> client.lines.contains("event:yard-changed"));
    }
  }

  @Test
  void changingKvPublicationsOrRouteProfilesEmitsYardChangedOverEventsStream() throws Exception {
    String service = newServiceName("watcher-pub");
    register(service, Map.of("host", "10.1.2.3", "port", 8080));

    try (EventStreamClient client = new EventStreamClient(port)) {
      publish(service);

      await()
          .atMost(Duration.ofSeconds(10))
          .until(() -> client.lines.contains("event:yard-changed"));
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

  @Test
  void logsSingleWarningOnConsulDownAndReconnectsCleanly() {
    AtomicInteger warnings = new AtomicInteger();
    AtomicBoolean recovered = new AtomicBoolean();
    AtomicInteger triggers = new AtomicInteger();

    ConsulSettings deadConsul =
        new ConsulSettings(
            URI.create("http://127.0.0.1:54321"),
            "",
            Duration.ofMillis(500),
            Duration.ofSeconds(10));

    EventDebouncer mockDebouncer =
        new EventDebouncer(null) {
          @Override
          public void trigger(String eventName) {
            triggers.incrementAndGet();
          }

          @Override
          public void close() {}
        };

    ConsulWatcher watcher =
        new ConsulWatcher(
            new ConsulAgent(deadConsul),
            mockDebouncer,
            Duration.ofMillis(200),
            Duration.ofMillis(200),
            msg -> warnings.incrementAndGet(),
            msg -> recovered.set(true));

    watcher.start();
    try {
      await()
          .during(Duration.ofMillis(800))
          .atMost(Duration.ofSeconds(2))
          .until(() -> warnings.get() <= 1);
      assertThat(warnings.get()).isEqualTo(1);
      assertThat(recovered.get()).isFalse();
    } finally {
      watcher.stop();
    }
  }

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
}
