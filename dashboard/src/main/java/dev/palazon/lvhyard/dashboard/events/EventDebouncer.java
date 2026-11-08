package dev.palazon.lvhyard.dashboard.events;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Buffers rapid event triggers in a quiet window before broadcasting them. */
@Component
public class EventDebouncer implements AutoCloseable {

  private final EventBroadcaster broadcaster;
  private final Duration quietWindow;
  private final ScheduledExecutorService scheduler;
  private final Set<String> pendingEvents = new LinkedHashSet<>();
  private final Object lock = new Object();
  private ScheduledFuture<?> scheduledFlush;

  /** When the oldest event that waits now was triggered, from {@link System#nanoTime}. */
  private long firstPending;

  private static final Duration LONGEST_WAIT = Duration.ofSeconds(2);

  public EventDebouncer(EventBroadcaster broadcaster) {
    this(broadcaster, Duration.ofMillis(300));
  }

  @Autowired
  public EventDebouncer(
      EventBroadcaster broadcaster,
      @Value("${lvh-yard.events.debounce-window:300ms}") Duration quietWindow) {
    this.broadcaster = broadcaster;
    this.quietWindow = quietWindow;
    this.scheduler =
        Executors.newSingleThreadScheduledExecutor(
            Thread.ofVirtual().name("event-debouncer-", 0).factory());
  }

  /**
   * Triggers a debounced event signal. Bursts within the quiet window are coalesced. Changes that
   * never stop, such as checks every second, would move the window forever, so the events go out at
   * most {@link #LONGEST_WAIT} after the first one.
   */
  public void trigger(String eventName) {
    synchronized (lock) {
      long now = System.nanoTime();
      if (pendingEvents.isEmpty()) {
        firstPending = now;
      }
      pendingEvents.add(eventName);
      if (scheduledFlush != null && !scheduledFlush.isDone()) {
        scheduledFlush.cancel(false);
      }
      long left = LONGEST_WAIT.toNanos() - (now - firstPending);
      scheduledFlush =
          scheduler.schedule(
              this::flush,
              Math.max(0, Math.min(quietWindow.toNanos(), left)),
              TimeUnit.NANOSECONDS);
    }
  }

  /** Flushes all pending distinct events to the broadcaster. */
  void flush() {
    List<String> events;
    synchronized (lock) {
      if (pendingEvents.isEmpty()) {
        return;
      }
      events = new ArrayList<>(pendingEvents);
      pendingEvents.clear();
      scheduledFlush = null;
    }
    for (String event : events) {
      broadcaster.broadcast(event);
    }
  }

  @Override
  public void close() {
    synchronized (lock) {
      if (scheduledFlush != null) {
        scheduledFlush.cancel(false);
      }
    }
    scheduler.shutdownNow();
  }
}
