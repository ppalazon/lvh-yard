package dev.palazon.lvhyard.dashboard.services;

import dev.palazon.lvhyard.dashboard.consul.ConsulSettings;
import dev.palazon.lvhyard.dashboard.reachability.TraefikSettings;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Keeps this Workstation's {@linkplain CoreService Core Services} on its Consul: the dashboard
 * ({@code yard}), Consul's web page ({@code consul}) and Traefik's admin page ({@code traefik}).
 * Each is a Static Instance on loopback with a fixed ID and an HTTP check, published with Local
 * Scope. On every start of the dashboard, whatever is missing or has moved is written again, and
 * the rest is left alone. A Consul that does not answer yet is tried again every few seconds.
 */
@Component
public class CoreServices implements SmartLifecycle {

  private static final Logger LOG = LoggerFactory.getLogger(CoreServices.class);

  /** How long to wait before trying a Consul that does not answer again. */
  private static final Duration RETRY = Duration.ofSeconds(5);

  private final Registrations registrations;

  private final Publications publications;

  private final ConsulSettings consul;

  private final TraefikSettings traefik;

  private final Environment environment;

  /** Whether it has started and not stopped since. */
  private volatile boolean running;

  /** The thread that tries Consul again, while one is needed. */
  private volatile Thread retryThread;

  CoreServices(
      Registrations registrations,
      Publications publications,
      ConsulSettings consul,
      TraefikSettings traefik,
      Environment environment) {
    this.registrations = registrations;
    this.publications = publications;
    this.consul = consul;
    this.traefik = traefik;
    this.environment = environment;
  }

  /**
   * Keeps the Core Services once, so they are there as soon as the dashboard has started, and, when
   * Consul does not answer yet, goes on trying in the background.
   */
  @Override
  public void start() {
    running = true;
    try {
      keep();
    } catch (RuntimeException e) {
      LOG.warn(
          "The Core Services cannot be kept on Consul yet; trying again every {} s: {}",
          RETRY.toSeconds(),
          e.getMessage());
      Thread starting = Thread.ofVirtual().name("core-services").unstarted(this::retry);
      retryThread = starting;
      starting.start();
    }
  }

  @Override
  public void stop() {
    running = false;
    Thread stopping = retryThread;
    retryThread = null;
    if (stopping != null) {
      stopping.interrupt();
    }
  }

  @Override
  public boolean isRunning() {
    return running;
  }

  /** Tries again until the Core Services are kept, or the dashboard stops. */
  private void retry() {
    while (retryThread == Thread.currentThread()) {
      try {
        Thread.sleep(RETRY);
      } catch (InterruptedException e) {
        return;
      }
      try {
        keep();
        LOG.info("Consul answers: the Core Services are kept");
        retryThread = null;
        return;
      } catch (RuntimeException e) {
        // Logged once, when the first try failed.
      }
    }
  }

  /** Writes each Core Service's Instance and Publication that is missing or has moved. */
  private void keep() {
    CoreService.Settings settings = settings();
    for (CoreService service : CoreService.values()) {
      registrations.keepStaticInstance(service, service.location(settings));
      publications.keepCorePublication(service);
    }
  }

  /**
   * This Workstation's settings that place the Core Services. The dashboard's port is known once
   * its web server has started.
   */
  private CoreService.Settings settings() {
    String dashboardPort =
        environment.getProperty(
            "local.server.port", environment.getRequiredProperty("server.port"));
    return new CoreService.Settings(
        environment.getRequiredProperty("server.address"),
        Integer.parseInt(dashboardPort),
        consul.url().getPort(),
        traefik.adminPort());
  }
}
