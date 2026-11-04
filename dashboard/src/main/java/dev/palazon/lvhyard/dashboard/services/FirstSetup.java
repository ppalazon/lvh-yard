package dev.palazon.lvhyard.dashboard.services;

import dev.palazon.lvhyard.dashboard.BindAddress;
import dev.palazon.lvhyard.dashboard.Workstation;
import dev.palazon.lvhyard.dashboard.consul.Consul;
import dev.palazon.lvhyard.dashboard.consul.ConsulSettings;
import dev.palazon.lvhyard.dashboard.reachability.TraefikSettings;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * On the first start against a Consul, it registers yard, consul-ui and traefik-admin and publishes
 * them with Local Scope, as a user's request would. Then it writes the time at {@value #MARKER} and
 * never does this again. A Service with one of the names that already exists is left alone. This
 * runs in the background, so a slow Consul never holds up the start.
 */
@Component
public class FirstSetup implements SmartLifecycle {

  private static final Logger LOG = LoggerFactory.getLogger(FirstSetup.class);

  public static final String MARKER = "lvh-yard/first-setup";

  private static final Duration RETRY = Duration.ofSeconds(5);

  /**
   * How long one try may take. It is checked between Consul calls, and each call gives up after 3
   * s, so a try ends at most that much later.
   */
  private static final Duration TRY_LIMIT = Duration.ofSeconds(10);

  private final Workstation workstation;

  private final Consul kv;

  private final Registrations registrations;

  private final Publications publications;

  private final ConsulSettings consul;

  private final TraefikSettings traefik;

  private final Environment environment;

  private volatile boolean running;

  private volatile Thread settingUpThread;

  FirstSetup(
      Workstation workstation,
      Consul kv,
      Registrations registrations,
      Publications publications,
      ConsulSettings consul,
      TraefikSettings traefik,
      Environment environment) {
    this.workstation = workstation;
    this.kv = kv;
    this.registrations = registrations;
    this.publications = publications;
    this.consul = consul;
    this.traefik = traefik;
    this.environment = environment;
  }

  @Override
  public void start() {
    running = true;
    Thread starting = Thread.ofVirtual().name("first-setup").unstarted(this::setUpUntilDone);
    settingUpThread = starting;
    starting.start();
  }

  @Override
  public void stop() {
    running = false;
    Thread stopping = settingUpThread;
    settingUpThread = null;
    if (stopping != null) {
      stopping.interrupt();
    }
  }

  @Override
  public boolean isRunning() {
    return running;
  }

  private void setUpUntilDone() {
    boolean failedBefore = false;
    // A Service that an earlier try started to write is ours, so a later try finishes it.
    Set<String> started = new HashSet<>();
    while (settingUpThread == Thread.currentThread()) {
      try {
        if (kv.values(null, MARKER).containsKey(MARKER)) {
          if (failedBefore) {
            LOG.info("Consul answers: the first setup was done before");
          }
        } else {
          List<String> written = setUp(Instant.now().plus(TRY_LIMIT), started);
          kv.putValue(MARKER, Instant.now().toString());
          LOG.info(
              "The first setup is done. It wrote these Services: {}",
              written.isEmpty() ? "none" : String.join(", ", written));
        }
        settingUpThread = null;
        return;
      } catch (RuntimeException e) {
        if (!failedBefore) {
          LOG.warn(
              "The first setup cannot reach Consul yet. It tries again every {} s: {}",
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

  private List<String> setUp(Instant deadline, Set<String> started) {
    List<String> written = new ArrayList<>();
    for (OwnService service : services()) {
      before(deadline);
      if (!started.contains(service.name()) && publications.exists(service.name())) {
        continue;
      }
      started.add(service.name());
      before(deadline);
      registrations.register(
          workstation.name(),
          service.name(),
          new StaticInstanceRequest(
              service.host(),
              service.port(),
              new HealthCheck(HealthCheck.HTTP, service.path(), null, null),
              List.of(),
              null));
      before(deadline);
      publications.publish(
          workstation.name(), service.name(), new PublicationRequest(Scope.LOCAL, null, null));
      written.add(service.name());
    }
    return written;
  }

  private static void before(Instant deadline) {
    if (Instant.now().isAfter(deadline)) {
      throw new IllegalStateException(
          "Consul took longer than " + TRY_LIMIT.toSeconds() + " s to answer");
    }
  }

  /** The dashboard's port is known once its web server has started. */
  private List<OwnService> services() {
    String dashboardPort =
        environment.getProperty(
            "local.server.port", environment.getRequiredProperty("server.port"));
    String loopback = "127.0.0.1";
    return List.of(
        new OwnService(
            "yard",
            BindAddress.host(environment.getRequiredProperty("server.address")),
            Integer.parseInt(dashboardPort),
            "/actuator/health"),
        new OwnService("consul-ui", loopback, consul.url().getPort(), "/v1/status/leader"),
        new OwnService("traefik-admin", loopback, traefik.adminPort(), "/ping"));
  }

  private record OwnService(String name, String host, int port, String path) {}
}
