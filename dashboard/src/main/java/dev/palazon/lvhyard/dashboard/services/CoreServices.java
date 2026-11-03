package dev.palazon.lvhyard.dashboard.services;

import dev.palazon.lvhyard.dashboard.consul.Consul;
import dev.palazon.lvhyard.dashboard.consul.ConsulSettings;
import dev.palazon.lvhyard.dashboard.reachability.TraefikSettings;
import java.time.Duration;
import java.time.Instant;
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
 * the rest is left alone. This is done in the background, so a slow Consul never holds up the
 * start: each try has a time limit, and a Consul that does not answer yet is tried again every few
 * seconds. When a try has finished, the time is written at {@value #INITIALIZED}.
 */
@Component
public class CoreServices implements SmartLifecycle {

  private static final Logger LOG = LoggerFactory.getLogger(CoreServices.class);

  /** The KV key where the time this start finished keeping the Core Services is written. */
  public static final String INITIALIZED = "lvh-yard/core-services/initialized";

  /** How long to wait before trying a Consul that does not answer again. */
  private static final Duration RETRY = Duration.ofSeconds(5);

  /**
   * How long one try may take. It is checked between Consul calls, and each call gives up after 3
   * s, so a try ends at most that much later.
   */
  private static final Duration TRY_LIMIT = Duration.ofSeconds(10);

  private final Consul kv;

  private final Registrations registrations;

  private final Publications publications;

  private final ConsulSettings consul;

  private final TraefikSettings traefik;

  private final Environment environment;

  /** Whether it has started and not stopped since. */
  private volatile boolean running;

  /** The thread that keeps the Core Services, until they are kept or the dashboard stops. */
  private volatile Thread keepingThread;

  CoreServices(
      Consul kv,
      Registrations registrations,
      Publications publications,
      ConsulSettings consul,
      TraefikSettings traefik,
      Environment environment) {
    this.kv = kv;
    this.registrations = registrations;
    this.publications = publications;
    this.consul = consul;
    this.traefik = traefik;
    this.environment = environment;
  }

  /** Starts keeping the Core Services in the background, so the start does not wait for Consul. */
  @Override
  public void start() {
    running = true;
    Thread starting = Thread.ofVirtual().name("core-services").unstarted(this::keepUntilKept);
    keepingThread = starting;
    starting.start();
  }

  @Override
  public void stop() {
    running = false;
    Thread stopping = keepingThread;
    keepingThread = null;
    if (stopping != null) {
      stopping.interrupt();
    }
  }

  @Override
  public boolean isRunning() {
    return running;
  }

  /**
   * Tries until the Core Services are kept, then writes the time at {@value #INITIALIZED}; or until
   * the dashboard stops.
   */
  private void keepUntilKept() {
    boolean failedBefore = false;
    while (keepingThread == Thread.currentThread()) {
      try {
        keep(Instant.now().plus(TRY_LIMIT));
        kv.putValue(INITIALIZED, Instant.now().toString());
        if (failedBefore) {
          LOG.info("Consul answers: the Core Services are kept");
        }
        keepingThread = null;
        return;
      } catch (RuntimeException e) {
        if (!failedBefore) {
          LOG.warn(
              "The Core Services cannot be kept on Consul yet; trying again every {} s: {}",
              RETRY.toSeconds(),
              e.getMessage());
          failedBefore = true;
        }
      }
      try {
        Thread.sleep(RETRY);
      } catch (InterruptedException e) {
        return;
      }
    }
  }

  /**
   * Writes each Core Service's Instance and Publication that is missing or has moved, giving up
   * when {@code deadline} has passed.
   */
  private void keep(Instant deadline) {
    CoreService.Settings settings = settings();
    for (CoreService service : CoreService.values()) {
      before(deadline);
      registrations.keepStaticInstance(service, service.location(settings));
      before(deadline);
      publications.keepCorePublication(service);
    }
  }

  /** Fails the try when {@code deadline} has passed. */
  private static void before(Instant deadline) {
    if (Instant.now().isAfter(deadline)) {
      throw new IllegalStateException(
          "Consul took longer than " + TRY_LIMIT.toSeconds() + " s to answer");
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
