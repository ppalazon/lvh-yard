package dev.palazon.lvhyard.dashboard.services;

import dev.palazon.lvhyard.dashboard.BindAddress;
import dev.palazon.lvhyard.dashboard.Workstation;
import dev.palazon.lvhyard.dashboard.consul.ConsulSettings;
import dev.palazon.lvhyard.dashboard.reachability.TraefikSettings;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Keeps this Workstation's Core Services on its Consul: the dashboard ({@code yard}), Consul's web
 * page ({@code consul}) and Traefik's admin page ({@code traefik}). Each is a Static Instance on
 * loopback with a fixed ID and an HTTP check, published with Local Scope. On every start of the
 * dashboard, whatever is missing or has moved is written again, and the rest is left alone. A
 * Consul that does not answer yet is tried again every few seconds.
 */
@Component
public class CoreServices implements SmartLifecycle {

  /** The names of the Core Services, which no other Service can take. */
  public static final Set<String> NAMES = Set.of(Workstation.RESERVED_NAME, "consul", "traefik");

  private static final Logger LOG = LoggerFactory.getLogger(CoreServices.class);

  /** How long to wait before trying a Consul that does not answer again. */
  private static final Duration RETRY = Duration.ofSeconds(5);

  /** Where every Core Instance is: loopback, so only its own Workstation reaches it. */
  private static final String LOOPBACK = "127.0.0.1";

  private final Registrations registrations;

  private final Publications publications;

  private final ConsulSettings consul;

  private final TraefikSettings traefik;

  private final Environment environment;

  private volatile Thread keeper;

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
    try {
      keep();
    } catch (RuntimeException e) {
      LOG.warn(
          "The Core Services cannot be kept on Consul yet; trying again every {} s: {}",
          RETRY.toSeconds(),
          e.getMessage());
      Thread starting = Thread.ofVirtual().name("core-services").unstarted(this::retry);
      keeper = starting;
      starting.start();
    }
  }

  @Override
  public void stop() {
    Thread stopping = keeper;
    keeper = null;
    if (stopping != null) {
      stopping.interrupt();
    }
  }

  @Override
  public boolean isRunning() {
    return keeper != null;
  }

  /** Tries again until the Core Services are kept, or the dashboard stops. */
  private void retry() {
    while (keeper == Thread.currentThread()) {
      try {
        Thread.sleep(RETRY);
      } catch (InterruptedException e) {
        return;
      }
      try {
        keep();
        LOG.info("Consul answers: the Core Services are kept");
        keeper = null;
        return;
      } catch (RuntimeException e) {
        // Logged once, when the first try failed.
      }
    }
  }

  /** Writes each Core Service's Instance and Publication that is missing or has moved. */
  private void keep() {
    for (CoreService service : wanted()) {
      registrations.keepCoreInstance(
          "lvh-yard-core-" + service.name(),
          service.name(),
          service.host(),
          service.port(),
          service.path());
      publications.keepCorePublication(service.name());
    }
  }

  /**
   * The Core Services as this Workstation's settings place them. The dashboard is where it listens,
   * which is known once its web server has started; on {@code 0.0.0.0}, it is reached at loopback.
   */
  private List<CoreService> wanted() {
    String dashboardPort =
        environment.getProperty(
            "local.server.port", environment.getRequiredProperty("server.port"));
    return List.of(
        new CoreService(
            Workstation.RESERVED_NAME,
            BindAddress.host(environment.getRequiredProperty("server.address")),
            Integer.parseInt(dashboardPort),
            "/actuator/health"),
        new CoreService("consul", LOOPBACK, consul.url().getPort(), "/v1/status/leader"),
        new CoreService("traefik", LOOPBACK, traefik.adminPort(), "/ping"));
  }

  /** A Core Service's Instance: where it is, and the path its HTTP check GETs. */
  private record CoreService(String name, String host, int port, String path) {}
}
