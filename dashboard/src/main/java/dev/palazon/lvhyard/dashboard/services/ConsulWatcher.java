package dev.palazon.lvhyard.dashboard.services;

import static dev.palazon.lvhyard.dashboard.events.EventBroadcaster.YARD_CHANGED;

import dev.palazon.lvhyard.dashboard.consul.ConsulSettings;
import dev.palazon.lvhyard.dashboard.events.EventDebouncer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * Watches the local Consul agent for Instance Health transitions, KV mutations, Service
 * registrations, and WAN pool membership changes, emitting {@code yard-changed} events.
 */
@Component
public class ConsulWatcher implements SmartLifecycle {

  private static final Logger LOG = LoggerFactory.getLogger(ConsulWatcher.class);

  private static final Duration DEFAULT_BACKOFF = Duration.ofSeconds(5);
  private static final Duration DEFAULT_WAIT = Duration.ofSeconds(60);

  private final URI consulBaseUri;
  private final EventDebouncer debouncer;
  private final Duration backoffPause;
  private final Duration queryWait;
  private final Consumer<String> warnLogger;
  private final Consumer<String> infoLogger;

  private final HttpClient httpClient;
  private final AtomicBoolean failing = new AtomicBoolean(false);
  private volatile boolean running;
  private ExecutorService executor;

  @Autowired
  public ConsulWatcher(ConsulSettings settings, EventDebouncer debouncer) {
    this(settings, debouncer, DEFAULT_BACKOFF, DEFAULT_WAIT, LOG::warn, LOG::info);
  }

  public ConsulWatcher(
      ConsulSettings settings,
      EventDebouncer debouncer,
      Duration backoffPause,
      Duration queryWait,
      Consumer<String> warnLogger,
      Consumer<String> infoLogger) {
    this.consulBaseUri = settings.url();
    this.debouncer = debouncer;
    this.backoffPause = backoffPause;
    this.queryWait = queryWait;
    this.warnLogger = warnLogger;
    this.infoLogger = infoLogger;
    this.httpClient = HttpClient.newBuilder().connectTimeout(backoffPause).build();
  }

  @Override
  public synchronized void start() {
    if (running) {
      return;
    }
    running = true;
    executor =
        Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("consul-watcher-", 0).factory());
    executor.submit(() -> watchBlocking("/v1/health/state/any"));
    executor.submit(() -> watchBlocking("/v1/kv/lvh-yard/?recurse=true"));
    executor.submit(() -> watchBlocking("/v1/catalog/services"));
    executor.submit(this::watchWanMembers);
  }

  @Override
  public synchronized void stop() {
    running = false;
    if (executor != null) {
      executor.shutdownNow();
      executor = null;
    }
  }

  @Override
  public synchronized boolean isRunning() {
    return running;
  }

  private void watchBlocking(String path) {
    long index = 0;

    while (running && !Thread.currentThread().isInterrupted()) {
      try {
        QueryOutcome outcome = queryBlocking(path, index, queryWait);
        recovered();

        if (index > 0 && outcome.index() != index) {
          debouncer.trigger(YARD_CHANGED);
        }
        index = outcome.index();
      } catch (Exception e) {
        if (!running || Thread.currentThread().isInterrupted()) {
          return;
        }
        failed(e);
        if (!pause()) {
          return;
        }
      }
    }
  }

  private void watchWanMembers() {
    String lastBody = "";

    while (running && !Thread.currentThread().isInterrupted()) {
      try {
        WanOutcome outcome = queryWanMembers();
        recovered();

        if (!lastBody.isEmpty() && !Objects.equals(lastBody, outcome.body())) {
          debouncer.trigger(YARD_CHANGED);
        }
        lastBody = outcome.body();

        // WAN members endpoint does not support blocking queries; poll every backoffPause
        Thread.sleep(backoffPause);
      } catch (InterruptedException e) {
        return;
      } catch (Exception e) {
        if (!running || Thread.currentThread().isInterrupted()) {
          return;
        }
        failed(e);
        if (!pause()) {
          return;
        }
      }
    }
  }

  private void recovered() {
    if (failing.compareAndSet(true, false)) {
      infoLogger.accept("Consul answers again: the Yard is in step with its state");
    }
  }

  private void failed(Exception e) {
    if (failing.compareAndSet(false, true)) {
      warnLogger.accept(
          "Consul cannot be followed, so the Yard may be out of step until it can;"
              + " trying again every "
              + backoffPause.toSeconds()
              + " s: "
              + e.getMessage());
    }
  }

  private QueryOutcome queryBlocking(String path, long index, Duration wait) throws Exception {
    String waitParam = wait.toSeconds() + "s";
    String uri =
        consulBaseUri.toString()
            + path
            + (path.contains("?") ? "&" : "?")
            + "index="
            + index
            + "&wait="
            + waitParam;

    HttpRequest request =
        HttpRequest.newBuilder().uri(URI.create(uri)).timeout(wait.plusSeconds(5)).GET().build();

    HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());

    int status = response.statusCode();
    if (status != 200 && status != 404) {
      throw new IllegalStateException("Consul answered with HTTP status " + status);
    }

    long newIndex = index;
    var indexHeader = response.headers().firstValue("X-Consul-Index");
    if (indexHeader.isPresent()) {
      try {
        newIndex = Long.parseLong(indexHeader.get());
      } catch (NumberFormatException ignored) {
      }
    }
    return new QueryOutcome(newIndex);
  }

  private WanOutcome queryWanMembers() throws Exception {
    String uri = consulBaseUri.toString() + "/v1/agent/members?wan=1";
    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(uri))
            .timeout(queryWait.isZero() ? Duration.ofSeconds(5) : queryWait)
            .GET()
            .build();

    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

    if (response.statusCode() != 200) {
      throw new IllegalStateException("Consul answered with HTTP status " + response.statusCode());
    }
    return new WanOutcome(response.body());
  }

  private boolean pause() {
    try {
      Thread.sleep(backoffPause);
      return running && !Thread.currentThread().isInterrupted();
    } catch (InterruptedException e) {
      return false;
    }
  }

  private record QueryOutcome(long index) {}

  private record WanOutcome(String body) {}
}
