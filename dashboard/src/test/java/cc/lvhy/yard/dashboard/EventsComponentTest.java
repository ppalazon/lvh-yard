package cc.lvhy.yard.dashboard;

import static cc.lvhy.yard.dashboard.events.EventBroadcaster.CONTAINERS_CHANGED;
import static cc.lvhy.yard.dashboard.events.EventBroadcaster.YARD_CHANGED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import cc.lvhy.yard.dashboard.events.EventBroadcaster;
import cc.lvhy.yard.dashboard.events.EventDebouncer;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Server-Sent Events stream foundation, debouncer, keepalive and layout triggers. */
class EventsComponentTest extends DashboardComponentTest {

  @Autowired EventBroadcaster broadcaster;

  @Autowired EventDebouncer debouncer;

  @Test
  void connectsToEventsStreamAndEmitsHeartbeatComments() throws Exception {
    try (EventStreamClient client = new EventStreamClient(port)) {
      assertThat(client.response.statusCode()).isEqualTo(200);
      assertThat(client.response.headers().firstValue("Content-Type"))
          .hasValueSatisfying(type -> assertThat(type).startsWith("text/event-stream"));
      broadcaster.sendHeartbeat();
      await().atMost(Duration.ofSeconds(5)).until(() -> client.lines.contains(": ping"));
    }
  }

  @Test
  void debouncerCoalescesRapidTriggers() throws Exception {
    try (EventStreamClient client = new EventStreamClient(port)) {
      // Let startup triggers flush, so only the triggers below are counted
      AtomicInteger seen = new AtomicInteger(-1);
      await()
          .during(Duration.ofMillis(600))
          .atMost(Duration.ofSeconds(5))
          .until(() -> seen.getAndSet(client.lines.size()) == client.lines.size());
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
      // A late second flush would add a line within the following windows
      await()
          .during(Duration.ofMillis(900))
          .atMost(Duration.ofSeconds(5))
          .untilAsserted(() -> {
            assertThat(client.lines.stream().filter(l -> l.equals("event:yard-changed")))
                .hasSize(1);
            assertThat(client.lines.stream().filter(l -> l.equals("event:containers-changed")))
                .hasSize(1);
          });
    }
  }

  @Test
  void cleansUpDisconnectedClients() throws Exception {
    int initialCount = settledClientCount();
    EventStreamClient client = new EventStreamClient(port);
    assertThat(broadcaster.clientCount()).isEqualTo(initialCount + 1);

    client.close();

    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> {
          broadcaster.sendHeartbeat();
          assertThat(broadcaster.clientCount()).isLessThanOrEqualTo(initialCount);
        });
  }

  /**
   * The count once the streams that earlier tests closed are gone. The broadcaster removes a closed
   * stream when a write to it fails, which can take two heartbeats. Without this wait, one of those
   * removals can land between the baseline and the next check, and the count is one too low.
   */
  private int settledClientCount() {
    AtomicInteger last = new AtomicInteger(-1);
    AtomicInteger steadyPolls = new AtomicInteger();
    await()
        .atMost(Duration.ofSeconds(10))
        .pollInterval(Duration.ofMillis(100))
        .until(() -> {
          broadcaster.sendHeartbeat();
          int now = broadcaster.clientCount();
          steadyPolls.set(now == last.getAndSet(now) ? steadyPolls.get() + 1 : 0);
          return steadyPolls.get() >= 5;
        });
    return last.get();
  }

  @Test
  void layoutIncludesHxSseAndConnectsToEvents() {
    String html = getPage("/").getBody();
    assertThat(html).contains("hx-sse:connect=\"/events\"");
  }
}
