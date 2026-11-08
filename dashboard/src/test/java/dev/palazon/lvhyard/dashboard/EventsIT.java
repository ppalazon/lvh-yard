package dev.palazon.lvhyard.dashboard;

import static dev.palazon.lvhyard.dashboard.events.EventBroadcaster.CONTAINERS_CHANGED;
import static dev.palazon.lvhyard.dashboard.events.EventBroadcaster.YARD_CHANGED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.palazon.lvhyard.dashboard.events.EventBroadcaster;
import dev.palazon.lvhyard.dashboard.events.EventDebouncer;
import dev.palazon.lvhyard.dashboard.web.Layout;
import htmlflow.HtmlFlow;
import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Server-Sent Events stream foundation, debouncer, keepalive and layout triggers. */
class EventsIT extends DashboardIT {

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
      // Allow any startup event triggers to settle and flush past the 300ms window
      Thread.sleep(500);
      client.lines.clear();

      debouncer.trigger(YARD_CHANGED);
      debouncer.trigger(YARD_CHANGED);
      debouncer.trigger(CONTAINERS_CHANGED);
      debouncer.trigger(YARD_CHANGED);

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
        .contains("<body hx-ext=\"sse\" hx-sse:connect=\"/events\">")
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
                main, "catalog", "/services", YARD_CHANGED, div -> div.text("content")))
        .__()
        .__()
        .__();
    assertThat(singleOut.toString()).contains("hx-trigger=\"yard-changed from:body, every 30s\"");

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
                List.of(CONTAINERS_CHANGED, YARD_CHANGED),
                div -> div.text("content")))
        .__()
        .__()
        .__();
    assertThat(multiOut.toString())
        .contains("hx-trigger=\"containers-changed from:body, yard-changed from:body, every 30s\"");

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
}
