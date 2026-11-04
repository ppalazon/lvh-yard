package dev.palazon.lvhyard.dashboard.services;

import dev.palazon.lvhyard.dashboard.BindAddress;
import dev.palazon.lvhyard.dashboard.Workstation;
import dev.palazon.lvhyard.dashboard.consul.Consul;
import dev.palazon.lvhyard.dashboard.consul.ConsulSettings;
import dev.palazon.lvhyard.dashboard.reachability.TraefikSettings;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * The first start of a dashboard against a Consul registers yard, consul-ui and traefik-admin and
 * publishes them with Local Scope, as a user's request would. Then the dashboard writes the time at
 * {@value #MARKER} and never does this again. A Service with one of the names that already exists
 * is left alone. This runs in the background, so a slow Consul never holds up the start.
 */
@Component
public class FirstSetup implements SmartLifecycle {

  private static final Logger LOG = LoggerFactory.getLogger(FirstSetup.class);

  public static final String MARKER = "lvh-yard/first-setup";

  /**
   * The names of the Services that the setup writes, comma-separated. The setup writes it before
   * its first Service, so a setup that stops halfway finishes these on the next start, and still
   * leaves alone a Service that existed before it. It starts with {@value #MARKER}, so one read
   * gives both.
   */
  static final String NAMES = MARKER + "-names";

  private static final Duration RETRY = Duration.ofSeconds(5);

  /**
   * How long one try may take. It is checked between Consul calls, and each call gives up after 3
   * s, so a try ends at most that much later.
   */
  private static final Duration TRY_LIMIT = Duration.ofSeconds(10);

  private final Workstation workstation;

  private final Consul consul;

  private final Registrations registrations;

  private final Publications publications;

  private final ConsulSettings consulSettings;

  private final TraefikSettings traefik;

  private final Environment environment;

  private volatile boolean running;

  private volatile Thread settingUpThread;

  FirstSetup(
      Workstation workstation,
      Consul consul,
      Registrations registrations,
      Publications publications,
      ConsulSettings consulSettings,
      TraefikSettings traefik,
      Environment environment) {
    this.workstation = workstation;
    this.consul = consul;
    this.registrations = registrations;
    this.publications = publications;
    this.consulSettings = consulSettings;
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
    while (settingUpThread == Thread.currentThread()) {
      try {
        Map<String, String> values = consul.values(null, MARKER);
        if (values.containsKey(MARKER)) {
          if (failedBefore) {
            LOG.info("Consul answers: the first setup was done before");
          }
        } else {
          List<String> written = setUp(Instant.now().plus(TRY_LIMIT), values.get(NAMES));
          consul.deleteValue(NAMES);
          consul.putValue(MARKER, Instant.now().toString());
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

  /**
   * @param names what an earlier try kept at {@value #NAMES}, or null when no try began
   */
  private List<String> setUp(Instant deadline, String names) {
    List<OwnService> services = services();
    List<String> toWrite;
    if (names == null) {
      toWrite = new ArrayList<>();
      for (OwnService service : services) {
        failIfPast(deadline);
        if (!publications.exists(service.name())) {
          toWrite.add(service.name());
        }
      }
      failIfPast(deadline);
      consul.putValue(NAMES, String.join(",", toWrite));
    } else {
      toWrite = Arrays.asList(names.split(","));
    }
    List<String> written = new ArrayList<>();
    for (OwnService service : services) {
      if (!toWrite.contains(service.name())) {
        continue;
      }
      failIfPast(deadline);
      // The same host and port again replaces the Instance, so a second try adds no other one.
      registrations.register(
          workstation.name(),
          service.name(),
          new StaticInstanceRequest(
              service.host(),
              service.port(),
              new HealthCheck(HealthCheck.HTTP, service.path(), null, null),
              List.of(),
              null,
              false));
      failIfPast(deadline);
      publications.publish(
          workstation.name(), service.name(), new PublicationRequest(Scope.LOCAL, null, null));
      written.add(service.name());
    }
    return written;
  }

  private static void failIfPast(Instant deadline) {
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
        new OwnService("consul-ui", loopback, consulSettings.url().getPort(), "/v1/status/leader"),
        new OwnService("traefik-admin", loopback, traefik.adminPort(), "/ping"));
  }

  private record OwnService(String name, String host, int port, String path) {}
}
