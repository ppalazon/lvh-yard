package dev.palazon.lvhyard.dashboard.docker;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Only the calls LVH Yard makes, with Docker's own names. A Docker that does not answer fails each
 * call with an unchecked exception. {@link DockerEngine} is the real one.
 */
public interface Docker {

  /** Fails when Docker does not answer. */
  void ping();

  Version version();

  List<Container> runningContainers();

  /** The running containers and the stopped ones. */
  List<Container> containers();

  /**
   * The events already past, then each one as it happens. Docker answers only once it has an event,
   * so this call waits, like reading the next one. Closing the events stops the wait.
   */
  ContainerEvents containerEvents(Instant since);

  /** {@link #hasNext} is {@code false} once Docker stops sending. */
  interface ContainerEvents extends Iterator<Event>, AutoCloseable {

    @Override
    void close();
  }

  record Version(
      @JsonProperty("Version") String version, @JsonProperty("ApiVersion") String apiVersion) {}

  record Event(@JsonProperty("Type") String type, @JsonProperty("Action") String action) {}

  /**
   * Docker leaves out what a container does not have, such as a port without a host port. Each name
   * has a leading {@code /}.
   */
  record Container(
      @JsonProperty("Id") String id,
      @JsonProperty("Names") List<String> names,
      @JsonProperty("Ports") List<Port> ports,
      @JsonProperty("NetworkSettings") NetworkSettings networkSettings,
      @JsonProperty("Labels") Map<String, String> labels,
      @JsonProperty("State") String state,
      @JsonProperty("Status") String status,
      @JsonProperty("Health") Health health) {

    /** How an older Docker, which has no {@code Health}, ends a running container's status. */
    static final Pattern HEALTH_IN_STATUS =
        Pattern.compile("\\((?:health: )?(starting|healthy|unhealthy)\\)$");

    static final String COMPOSE_PROJECT = "com.docker.compose.project";

    static final String COMPOSE_SERVICE = "com.docker.compose.service";

    public Container {
      names = names == null ? List.of() : names;
      ports = ports == null ? List.of() : ports;
      networkSettings = networkSettings == null ? new NetworkSettings(Map.of()) : networkSettings;
      labels = labels == null ? Map.of() : labels;
    }

    /** Docker's state is running, exited, created or another word. */
    public boolean running() {
      return "running".equals(state);
    }

    public boolean isNamed(String name) {
      return names.contains("/" + name);
    }

    /** Whatever the container is named. */
    public boolean isOfComposeService(String project, String service) {
      return project.equals(labels.get(COMPOSE_PROJECT))
          && service.equals(labels.get(COMPOSE_SERVICE));
    }

    public Optional<String> composeProject() {
      return Optional.ofNullable(labels.get(COMPOSE_PROJECT));
    }

    public Optional<String> composeService() {
      return Optional.ofNullable(labels.get(COMPOSE_SERVICE));
    }

    /**
     * Its HEALTHCHECK state: {@code starting}, {@code healthy} or {@code unhealthy}. None when it
     * has no HEALTHCHECK.
     */
    public Optional<String> healthState() {
      if (health != null && health.status() != null && !"none".equals(health.status())) {
        return Optional.of(health.status());
      }
      if (status == null) {
        return Optional.empty();
      }
      Matcher matcher = HEALTH_IN_STATUS.matcher(status.strip());
      return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    /** The first of its names, or its ID when it has none. */
    public String name() {
      return names.isEmpty() ? id : names.getFirst().substring(1);
    }

    public List<Port> published(int containerPort) {
      return ports.stream()
          .filter(port ->
              port.privatePort() == containerPort
                  && "tcp".equals(port.type())
                  && port.publicPort() != null)
          .toList();
    }

    /** On the first of its networks, by name, that gives one. None on the host's network. */
    public Optional<String> address() {
      return networkSettings.networks().entrySet().stream()
          .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
          .map(network -> network.getValue().ipAddress())
          .filter(address -> address != null && !address.isBlank())
          .findFirst();
    }
  }

  /** {@code status} is {@code none} when the image's HEALTHCHECK is turned off. */
  record Health(@JsonProperty("Status") String status) {}

  /**
   * {@code ip} is {@code 0.0.0.0} or {@code ::} for every host address. When the port is not
   * published, {@code publicPort} is null and {@code ip} is empty.
   */
  record Port(
      @JsonProperty("IP") String ip,
      @JsonProperty("PrivatePort") int privatePort,
      @JsonProperty("PublicPort") Integer publicPort,
      @JsonProperty("Type") String type) {}

  record NetworkSettings(@JsonProperty("Networks") Map<String, Network> networks) {

    public NetworkSettings {
      networks = networks == null ? Map.of() : networks;
    }
  }

  /** {@code ipAddress} is empty on the host's network. */
  record Network(@JsonProperty("IPAddress") String ipAddress) {}
}
