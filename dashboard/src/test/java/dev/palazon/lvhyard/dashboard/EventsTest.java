package dev.palazon.lvhyard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.palazon.lvhyard.dashboard.events.EventBroadcaster;
import dev.palazon.lvhyard.dashboard.events.EventDebouncer;
import dev.palazon.lvhyard.dashboard.web.Layout;
import htmlflow.HtmlFlow;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Server-Sent Events stream foundation, debouncer, keepalive and layout triggers. */
class EventsTest extends DashboardTest {

  @Autowired EventBroadcaster broadcaster;

  @Autowired EventDebouncer debouncer;

  @Test
  void connectsToEventsStream() throws Exception {
    try (EventStreamClient client = new EventStreamClient(port)) {
      assertThat(client.response.statusCode()).isEqualTo(200);
      assertThat(client.response.headers().firstValue("Content-Type"))
          .hasValueSatisfying(type -> assertThat(type).startsWith("text/event-stream"));
    }
  }

  @Test
  void emitsHeartbeatComments() throws Exception {
    try (EventStreamClient client = new EventStreamClient(port)) {
      broadcaster.sendHeartbeat();
      await().atMost(Duration.ofSeconds(5)).until(() -> client.lines.contains(": ping"));
    }
  }

  @Test
  void debouncerCoalescesRapidTriggers() throws Exception {
    try (EventStreamClient client = new EventStreamClient(port)) {
      // Drain any background startup signals before asserting debounce coalescing
      Thread.sleep(500);
      client.lines.clear();

      debouncer.trigger("yard-changed");
      debouncer.trigger("yard-changed");
      debouncer.trigger("containers-changed");
      debouncer.trigger("yard-changed");

      await()
          .atMost(Duration.ofSeconds(5))
          .until(() ->
              client.lines.contains("event:yard-changed")
                  && client.lines.contains("event:containers-changed"));

      long yardCount = client.lines.stream().filter(l -> l.equals("event:yard-changed")).count();
      long containerCount =
          client.lines.stream().filter(l -> l.equals("event:containers-changed")).count();

      assertThat(yardCount).isEqualTo(1);
      assertThat(containerCount).isEqualTo(1);
    }
  }

  @Test
  void cleansUpDisconnectedClients() throws Exception {
    int initialCount = broadcaster.clientCount();
    EventStreamClient client = new EventStreamClient(port);
    assertThat(broadcaster.clientCount()).isEqualTo(initialCount + 1);

    client.close();

    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> {
          broadcaster.sendHeartbeat();
          assertThat(broadcaster.clientCount()).isEqualTo(initialCount);
        });
  }

  @Test
  void layoutIncludesHxSseAndConnectsToEvents() {
    String html = getPage("/").getBody();
    assertThat(html)
        .contains("<body hx-ext=\"sse\" sse-connect=\"/events\">")
        .containsPattern(
            Pattern.compile(
                "<script src=\"/webjars/htmx\\.org/dist/ext/hx-sse\\.min\\.js\""
                    + " defer(=\"defer\")?></script>"));
  }

  @Test
  void refreshedContractRendersSseTriggers() {
    StringBuilder singleOut = new StringBuilder();
    HtmlFlow.doc(singleOut)
        .setIndented(false)
        .html()
        .body()
        .main()
        .of(main ->
            Layout.refreshed(
                main, "catalog", "/services", "yard-changed", div -> div.text("content")))
        .__()
        .__()
        .__();
    assertThat(singleOut.toString()).contains("hx-trigger=\"sse:yard-changed, every 30s\"");

    StringBuilder multiOut = new StringBuilder();
    HtmlFlow.doc(multiOut)
        .setIndented(false)
        .html()
        .body()
        .main()
        .of(main ->
            Layout.refreshed(
                main,
                "containers",
                "/containers",
                List.of("containers-changed", "yard-changed"),
                div -> div.text("content")))
        .__()
        .__()
        .__();
    assertThat(multiOut.toString())
        .contains("hx-trigger=\"sse:containers-changed, sse:yard-changed, every 30s\"");

    StringBuilder fallbackOut = new StringBuilder();
    HtmlFlow.doc(fallbackOut)
        .setIndented(false)
        .html()
        .body()
        .main()
        .of(main -> Layout.refreshed(main, "status", "/status", div -> div.text("content")))
        .__()
        .__()
        .__();
    assertThat(fallbackOut.toString()).contains("hx-trigger=\"every 5s\"");
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
              .name("test-sse-reader")
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
