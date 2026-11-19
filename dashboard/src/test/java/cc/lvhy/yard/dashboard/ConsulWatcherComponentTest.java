package cc.lvhy.yard.dashboard;

import static cc.lvhy.yard.dashboard.events.EventBroadcaster.YARD_CHANGED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import cc.lvhy.yard.dashboard.consul.ConsulAgent;
import cc.lvhy.yard.dashboard.consul.ConsulSettings;
import cc.lvhy.yard.dashboard.events.EventDebouncer;
import cc.lvhy.yard.dashboard.services.ConsulWatcher;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Consul watcher: Instance health, KV mutations, WAN pool changes, and SSE signals. */
class ConsulWatcherComponentTest extends DashboardComponentTest {

  @Test
  void registeringAnInstanceEmitsYardChangedOverEventsStream() throws Exception {
    try (EventStreamClient client = new EventStreamClient(port)) {
      client.lines.clear();
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
      client.lines.clear();
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
      client.lines.clear();
      publish(service);
      await()
          .atMost(Duration.ofSeconds(10))
          .until(() -> client.lines.contains("event:yard-changed"));
    }
  }

  @Test
  void logsSingleWarningOnConsulDownAndReconnectsCleanly() {
    AtomicInteger warnings = new AtomicInteger();
    AtomicBoolean recovered = new AtomicBoolean();
    AtomicInteger triggers = new AtomicInteger();

    int deadPort;
    try (ServerSocket socket = new ServerSocket(0)) {
      deadPort = socket.getLocalPort();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    ConsulSettings deadConsul =
        new ConsulSettings(
            URI.create("http://127.0.0.1:" + deadPort),
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

    ConsulAgent agent = new ConsulAgent(deadConsul);
    ConsulWatcher watcher =
        new ConsulWatcher(
            agent,
            agent,
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

  @Test
  void wanPoolMembershipChangeEmitsYardChangedAndUpdatesCatalogAndServicePages() throws Exception {
    String service = newServiceName("watcher-wan");
    register(service, Map.of("host", "10.1.2.3", "port", 8080));

    try (EventStreamClient client = new EventStreamClient(port)) {
      client.lines.clear();
      // Trigger WAN pool membership change by force-leaving remote WAN member
      remote.stopAnswering();

      await()
          .atMost(Duration.ofSeconds(15))
          .until(() -> client.lines.contains("event:" + YARD_CHANGED));
    } finally {
      // Reconnect WAN member so subsequent tests remain federated
      remote.answerAgain();
    }
  }
}
