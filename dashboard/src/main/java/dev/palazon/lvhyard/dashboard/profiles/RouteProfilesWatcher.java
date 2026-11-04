package dev.palazon.lvhyard.dashboard.profiles;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * Reads {@code route-profiles.yaml} again every {@link #EVERY}. It polls, because an editor that
 * saves by replacing the file, or a file in a container's bind mount, may send no file event.
 */
@Component
class RouteProfilesWatcher implements SmartLifecycle {

  private static final Logger LOG = LoggerFactory.getLogger(RouteProfilesWatcher.class);

  private static final Duration EVERY = Duration.ofSeconds(1);

  private final RouteProfiles profiles;

  private volatile Thread watcher;

  RouteProfilesWatcher(RouteProfiles profiles) {
    this.profiles = profiles;
  }

  @Override
  public void start() {
    // Started only once it is the watcher, which it checks to know it may go on.
    Thread starting = Thread.ofVirtual().name("route-profiles-watcher").unstarted(this::watch);
    watcher = starting;
    starting.start();
  }

  @Override
  public void stop() {
    Thread stopping = watcher;
    watcher = null;
    if (stopping != null) {
      stopping.interrupt();
    }
  }

  @Override
  public boolean isRunning() {
    return watcher != null;
  }

  private void watch() {
    boolean failing = false;
    while (watcher == Thread.currentThread()) {
      try {
        profiles.refresh();
        if (failing) {
          LOG.info("Consul answers again: it has the Route Profiles of route-profiles.yaml");
          failing = false;
        }
      } catch (RuntimeException e) {
        if (!failing) {
          LOG.warn(
              "The Route Profiles cannot be copied to Consul; trying again every {} s: {}",
              EVERY.toSeconds(),
              e.getMessage());
          failing = true;
        }
      }
      try {
        Thread.sleep(EVERY);
      } catch (InterruptedException e) {
        return;
      }
    }
  }
}
