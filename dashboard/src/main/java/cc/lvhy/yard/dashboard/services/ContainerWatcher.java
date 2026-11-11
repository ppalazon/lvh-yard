package cc.lvhy.yard.dashboard.services;

import static cc.lvhy.yard.dashboard.events.EventBroadcaster.CONTAINERS_CHANGED;

import cc.lvhy.yard.dashboard.docker.Docker;
import cc.lvhy.yard.dashboard.docker.Docker.ContainerEvents;
import cc.lvhy.yard.dashboard.events.EventDebouncer;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * Syncs on startup and after each container event, so changes while the dashboard was down are
 * caught up with. A Docker that does not answer is tried again every {@link #RETRY}.
 */
@Component
class ContainerWatcher implements SmartLifecycle {

  private static final Logger LOG = LoggerFactory.getLogger(ContainerWatcher.class);

  private static final Duration RETRY = Duration.ofSeconds(5);

  /**
   * How long before a sync its events are asked from. Docker counts in whole seconds, and its clock
   * may be a little behind; an event seen twice only syncs again.
   */
  private static final Duration CLOCK_MARGIN = Duration.ofSeconds(2);

  private final Docker docker;

  private final ContainerBindings bindings;

  private final EventDebouncer debouncer;

  private volatile Thread watcher;

  /** The events being followed now, closed to stop the wait for the next one. */
  private volatile ContainerEvents events;

  ContainerWatcher(Docker docker, ContainerBindings bindings, EventDebouncer debouncer) {
    this.docker = docker;
    this.bindings = bindings;
    this.debouncer = debouncer;
  }

  @Override
  public void start() {
    // Started only once it is the watcher, which it checks to know it may go on.
    Thread starting = Thread.ofVirtual().name("container-watcher").unstarted(this::watch);
    watcher = starting;
    starting.start();
  }

  @Override
  public void stop() {
    Thread stopping = watcher;
    watcher = null;
    ContainerEvents following = events;
    if (following != null) {
      following.close();
    }
    if (stopping != null) {
      stopping.interrupt();
    }
  }

  @Override
  public boolean isRunning() {
    return watcher != null;
  }

  /**
   * Follows Docker until stopped. The events are asked for from just before each sync, so a
   * container that changes during the sync is not missed: Docker sends its event once asked.
   */
  private void watch() {
    boolean failing = false;
    while (watcher == Thread.currentThread()) {
      Instant synced = Instant.now().minus(CLOCK_MARGIN);
      try {
        bindings.sync();
      } catch (RuntimeException e) {
        failing = failed(failing, e);
        if (!pause()) {
          return;
        }
        continue;
      }
      if (failing) {
        LOG.info("Docker answers again: the Bound Instances are in step with its containers");
        failing = false;
      }
      try (ContainerEvents following = docker.containerEvents(synced)) {
        events = following;
        // Asking waits for an event, so the watcher may have been stopped meanwhile, with nothing
        // to close.
        while (watcher == Thread.currentThread() && following.hasNext()) {
          following.next();
          if (watcher == Thread.currentThread()) {
            bindings.sync();
            debouncer.trigger(CONTAINERS_CHANGED);
          }
        }
      } catch (RuntimeException e) {
        if (watcher != Thread.currentThread()) {
          return;
        }
        failing = failed(failing, e);
      } finally {
        events = null;
      }
      if (!pause()) {
        return;
      }
    }
  }

  /** Logs only the first of a run of failures. */
  private static boolean failed(boolean failing, RuntimeException e) {
    if (!failing) {
      LOG.warn(
          "Docker cannot be followed, so the Bound Instances may be out of step until it can;"
              + " trying again every {} s: {}",
          RETRY.toSeconds(),
          e.getMessage());
    }
    return true;
  }

  /** {@code false} when the watcher is stopped meanwhile. */
  private boolean pause() {
    try {
      Thread.sleep(RETRY);
      return watcher == Thread.currentThread();
    } catch (InterruptedException e) {
      return false;
    }
  }
}
