package cc.lvhy.yard.dashboard.services;

import static cc.lvhy.yard.dashboard.events.EventBroadcaster.YARD_CHANGED;

import cc.lvhy.yard.dashboard.consul.Consul.WanMember;
import cc.lvhy.yard.dashboard.consul.ConsulFederation;
import cc.lvhy.yard.dashboard.consul.ConsulWatching;
import cc.lvhy.yard.dashboard.consul.ConsulWatching.Watched;
import cc.lvhy.yard.dashboard.events.EventDebouncer;
import java.time.Duration;
import java.util.List;
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

  private final ConsulWatching watching;
  private final ConsulFederation federation;
  private final EventDebouncer debouncer;
  private final Duration backoffPause;
  private final Duration queryWait;
  private final Consumer<String> warnLogger;
  private final Consumer<String> infoLogger;

  private final AtomicBoolean failing = new AtomicBoolean(false);
  private volatile boolean running;
  private ExecutorService executor;

  @Autowired
  public ConsulWatcher(
      ConsulWatching watching, ConsulFederation federation, EventDebouncer debouncer) {
    this(watching, federation, debouncer, DEFAULT_BACKOFF, DEFAULT_WAIT, LOG::warn, LOG::info);
  }

  public ConsulWatcher(
      ConsulWatching watching,
      ConsulFederation federation,
      EventDebouncer debouncer,
      Duration backoffPause,
      Duration queryWait,
      Consumer<String> warnLogger,
      Consumer<String> infoLogger) {
    this.watching = watching;
    this.federation = federation;
    this.debouncer = debouncer;
    this.backoffPause = backoffPause;
    this.queryWait = queryWait;
    this.warnLogger = warnLogger;
    this.infoLogger = infoLogger;
  }

  @Override
  public synchronized void start() {
    if (running) {
      return;
    }
    running = true;
    executor =
        Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("consul-watcher-", 0).factory());
    for (Watched watched : Watched.values()) {
      executor.submit(() -> watchBlocking(watched));
    }
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

  private void watchBlocking(Watched watched) {
    long index = 0;

    while (running && !Thread.currentThread().isInterrupted()) {
      try {
        long changed = watching.awaitChange(watched, index, queryWait);
        recovered();

        if (index > 0 && changed != index) {
          debouncer.trigger(YARD_CHANGED);
        }
        index = changed;
      } catch (RuntimeException e) {
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

  /** The WAN members have no blocking query, so they are read every {@code backoffPause}. */
  private void watchWanMembers() {
    List<WanMember> last = null;

    while (running && !Thread.currentThread().isInterrupted()) {
      try {
        List<WanMember> members = federation.wanMembers();
        recovered();

        if (last != null && !last.equals(members)) {
          debouncer.trigger(YARD_CHANGED);
        }
        last = members;

        Thread.sleep(backoffPause);
      } catch (InterruptedException e) {
        return;
      } catch (RuntimeException e) {
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

  private boolean pause() {
    try {
      Thread.sleep(backoffPause);
      return running && !Thread.currentThread().isInterrupted();
    } catch (InterruptedException e) {
      return false;
    }
  }
}
