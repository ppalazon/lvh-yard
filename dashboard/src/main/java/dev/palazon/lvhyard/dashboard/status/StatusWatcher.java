package dev.palazon.lvhyard.dashboard.status;

import static dev.palazon.lvhyard.dashboard.events.EventBroadcaster.STATUS_CHANGED;

import dev.palazon.lvhyard.dashboard.events.EventDebouncer;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * Periodically polls the status parts and emits {@code status-changed} when any part transitions
 * state.
 */
@Component
public class StatusWatcher implements SmartLifecycle {

  private static final Duration POLL_INTERVAL = Duration.ofSeconds(2);

  private final StatusReader statusReader;
  private final EventDebouncer debouncer;
  private final Duration pollInterval;

  private final Map<String, PartState> lastStates = new HashMap<>();
  private volatile Thread watcherThread;
  private volatile boolean running;

  @Autowired
  public StatusWatcher(StatusReader statusReader, EventDebouncer debouncer) {
    this(statusReader, debouncer, POLL_INTERVAL);
  }

  public StatusWatcher(StatusReader statusReader, EventDebouncer debouncer, Duration pollInterval) {
    this.statusReader = statusReader;
    this.debouncer = debouncer;
    this.pollInterval = pollInterval;
  }

  @Override
  public synchronized void start() {
    if (running) {
      return;
    }
    running = true;
    watcherThread = Thread.ofVirtual().name("status-watcher").unstarted(this::pollLoop);
    watcherThread.start();
  }

  @Override
  public synchronized void stop() {
    running = false;
    Thread thread = watcherThread;
    watcherThread = null;
    if (thread != null) {
      thread.interrupt();
    }
  }

  @Override
  public boolean isRunning() {
    return running;
  }

  private void pollLoop() {
    while (running) {
      try {
        checkStatus();
        Thread.sleep(pollInterval.toMillis());
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      } catch (Exception ignored) {
      }
    }
  }

  void checkStatus() {
    Status status = statusReader.status();
    boolean changed = false;
    for (Part part : status.parts()) {
      PartState previous = lastStates.get(part.name());
      if (previous != null && previous != part.state()) {
        changed = true;
      }
      lastStates.put(part.name(), part.state());
    }
    if (changed) {
      debouncer.trigger(STATUS_CHANGED);
    }
  }
}
