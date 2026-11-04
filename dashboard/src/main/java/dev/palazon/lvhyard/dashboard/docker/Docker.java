package dev.palazon.lvhyard.dashboard.docker;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.dockerjava.transport.DockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient.Request;
import com.github.dockerjava.transport.DockerHttpClient.Response;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MappingIterator;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Only the calls LVH Yard makes, with Docker's own names. A Docker that does not answer fails each
 * call with an unchecked exception.
 */
@Component
public class Docker {

  /**
   * How long connecting may take: Docker is on this Workstation, so it answers at once or not at
   * all.
   */
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);

  /** The container events that can change which containers run, or under which name. */
  private static final String CONTAINER_EVENTS =
      "{\"type\":[\"container\"]," + "\"event\":[\"start\",\"die\",\"destroy\",\"rename\"]}";

  private static final ObjectMapper JSON =
      JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

  private final DockerHttpClient http;

  Docker(DockerSettings settings) {
    this.http =
        new ZerodepDockerHttpClient.Builder()
            .dockerHost(settings.host())
            .connectionTimeout(CONNECT_TIMEOUT)
            .build();
  }

  /** Once the watcher has stopped following Docker. */
  @PreDestroy
  void close() throws IOException {
    http.close();
  }

  /** Fails when Docker does not answer. */
  public void ping() {
    try (Response response = http.execute(get("/_ping"))) {
      checkOk(response);
    }
  }

  public Version version() {
    try (Response response = http.execute(get("/version"))) {
      checkOk(response);
      return JSON.readValue(response.getBody(), Version.class);
    } catch (JacksonException e) {
      throw new IllegalStateException("Docker answered a version that cannot be read", e);
    }
  }

  public List<Container> runningContainers() {
    try (Response response = http.execute(get("/containers/json"))) {
      checkOk(response);
      return JSON.readValue(response.getBody(), new TypeReference<List<Container>>() {});
    } catch (JacksonException e) {
      throw new IllegalStateException("Docker answered containers that cannot be read", e);
    }
  }

  /**
   * The events already past, then each one as it happens. Docker answers only once it has an event,
   * so this call waits, like reading the next one. Closing the events stops the wait.
   */
  public ContainerEvents containerEvents(Instant since) {
    Response response =
        http.execute(
            get(
                "/events?since="
                    + since.getEpochSecond()
                    + "&filters="
                    + URLEncoder.encode(CONTAINER_EVENTS, StandardCharsets.UTF_8)));
    try {
      checkOk(response);
      return new ContainerEvents(
          response, JSON.readerFor(Event.class).readValues(response.getBody()));
    } catch (RuntimeException e) {
      response.close();
      throw e;
    }
  }

  private static Request get(String path) {
    return Request.builder().method(Request.Method.GET).path(path).build();
  }

  private static void checkOk(Response response) {
    if (response.getStatusCode() / 100 != 2) {
      String body;
      try (InputStream in = response.getBody()) {
        body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
      throw new IllegalStateException(
          "Docker answered " + response.getStatusCode() + ": " + body.strip());
    }
  }

  public static final class ContainerEvents implements Iterator<Event>, AutoCloseable {

    private final Response response;

    private final MappingIterator<Event> events;

    private ContainerEvents(Response response, MappingIterator<Event> events) {
      this.response = response;
      this.events = events;
    }

    /** {@code false} once Docker stops sending. */
    @Override
    public boolean hasNext() {
      return events.hasNext();
    }

    @Override
    public Event next() {
      return events.next();
    }

    @Override
    public void close() {
      response.close();
    }
  }

  public record Version(
      @JsonProperty("Version") String version, @JsonProperty("ApiVersion") String apiVersion) {}

  public record Event(@JsonProperty("Type") String type, @JsonProperty("Action") String action) {}

  /**
   * Docker leaves out what a container does not have, such as a port without a host port. Each name
   * has a leading {@code /}.
   */
  public record Container(
      @JsonProperty("Id") String id,
      @JsonProperty("Names") List<String> names,
      @JsonProperty("Ports") List<Port> ports,
      @JsonProperty("NetworkSettings") NetworkSettings networkSettings,
      @JsonProperty("Labels") Map<String, String> labels,
      @JsonProperty("Status") String status,
      @JsonProperty("Health") Health health) {

    /** How an older Docker, which has no {@code Health}, ends a running container's status. */
    private static final Pattern HEALTH_IN_STATUS =
        Pattern.compile("\\((?:health: )?(starting|healthy|unhealthy)\\)$");

    private static final String COMPOSE_PROJECT = "com.docker.compose.project";

    private static final String COMPOSE_SERVICE = "com.docker.compose.service";

    public Container {
      names = names == null ? List.of() : names;
      ports = ports == null ? List.of() : ports;
      networkSettings = networkSettings == null ? new NetworkSettings(Map.of()) : networkSettings;
      labels = labels == null ? Map.of() : labels;
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
  public record Health(@JsonProperty("Status") String status) {}

  /**
   * {@code ip} is {@code 0.0.0.0} or {@code ::} for every host address. {@code ip} and {@code
   * publicPort} are null when the port is not published.
   */
  public record Port(
      @JsonProperty("IP") String ip,
      @JsonProperty("PrivatePort") int privatePort,
      @JsonProperty("PublicPort") Integer publicPort,
      @JsonProperty("Type") String type) {}

  public record NetworkSettings(@JsonProperty("Networks") Map<String, Network> networks) {

    public NetworkSettings {
      networks = networks == null ? Map.of() : networks;
    }
  }

  /** {@code ipAddress} is empty on the host's network. */
  public record Network(@JsonProperty("IPAddress") String ipAddress) {}
}
