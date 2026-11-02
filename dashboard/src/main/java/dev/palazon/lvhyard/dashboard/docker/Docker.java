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
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MappingIterator;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * This Workstation's Docker, through its Engine API. Only the calls LVH Yard makes, with Docker's
 * own names; it knows nothing of the Yard's rules. A Docker that does not answer fails each call
 * with an unchecked exception.
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

  /** Closes the connections to Docker, once the watcher has stopped following it. */
  @PreDestroy
  void close() throws IOException {
    http.close();
  }

  /** Every running container. */
  public List<Container> runningContainers() {
    try (Response response = http.execute(get("/containers/json"))) {
      checkOk(response);
      return JSON.readValue(response.getBody(), new TypeReference<List<Container>>() {});
    } catch (JacksonException e) {
      throw new IllegalStateException("Docker answered containers that cannot be read", e);
    }
  }

  /**
   * The container events since {@code since} that can change which containers run: those already
   * past, then each one as it happens. Docker answers only once it has an event to send, so asking,
   * like reading the next one, waits; closing the events stops the wait.
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

  /**
   * Container events as Docker sends them, one at a time, until they are closed or Docker stops
   * sending.
   */
  public static final class ContainerEvents implements Iterator<Event>, AutoCloseable {

    private final Response response;

    private final MappingIterator<Event> events;

    private ContainerEvents(Response response, MappingIterator<Event> events) {
      this.response = response;
      this.events = events;
    }

    /** Waits for the next event; {@code false} once Docker stops sending. */
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

  /**
   * One event.
   *
   * @param type what it is about, such as {@code container}
   * @param action what happened, such as {@code start} or {@code die}
   */
  public record Event(@JsonProperty("Type") String type, @JsonProperty("Action") String action) {}

  /**
   * A running container. Docker leaves out what a container does not have, such as a port without a
   * host port.
   *
   * @param id its ID
   * @param names its names, each with a leading {@code /}
   * @param ports its ports, each once for every host address it is published on
   * @param networkSettings the networks it is on
   * @param labels its labels, such as those Compose gives the containers it starts
   */
  public record Container(
      @JsonProperty("Id") String id,
      @JsonProperty("Names") List<String> names,
      @JsonProperty("Ports") List<Port> ports,
      @JsonProperty("NetworkSettings") NetworkSettings networkSettings,
      @JsonProperty("Labels") Map<String, String> labels) {

    /** The label Compose gives each container it starts, with the project's name. */
    private static final String COMPOSE_PROJECT = "com.docker.compose.project";

    /** The label Compose gives each container it starts, with the service's name. */
    private static final String COMPOSE_SERVICE = "com.docker.compose.service";

    public Container {
      names = names == null ? List.of() : names;
      ports = ports == null ? List.of() : ports;
      networkSettings = networkSettings == null ? new NetworkSettings(Map.of()) : networkSettings;
      labels = labels == null ? Map.of() : labels;
    }

    /**
     * Whether {@code name}, as {@code docker run --name} takes it, is one of the container's names.
     */
    public boolean isNamed(String name) {
      return names.contains("/" + name);
    }

    /** Whether Compose started it for {@code service} of {@code project}, whatever it named it. */
    public boolean isOfComposeService(String project, String service) {
      return project.equals(labels.get(COMPOSE_PROJECT))
          && service.equals(labels.get(COMPOSE_SERVICE));
    }

    /**
     * Its name, as {@code docker run --name} takes it: the first of its names, or its ID when it
     * has none.
     */
    public String name() {
      return names.isEmpty() ? id : names.getFirst().substring(1);
    }

    /**
     * The TCP ports Docker publishes {@code containerPort} on, each with the host address it
     * listens on.
     */
    public List<Port> published(int containerPort) {
      return ports.stream()
          .filter(port ->
              port.privatePort() == containerPort
                  && "tcp".equals(port.type())
                  && port.publicPort() != null)
          .toList();
    }

    /**
     * The container's own address, on the first of its networks by name that gives it one. A
     * container on the host's network has none.
     */
    public Optional<String> address() {
      return networkSettings.networks().entrySet().stream()
          .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
          .map(network -> network.getValue().ipAddress())
          .filter(address -> address != null && !address.isBlank())
          .findFirst();
    }
  }

  /**
   * A container port, and where Docker publishes it.
   *
   * @param ip the host address it is published on, {@code 0.0.0.0} or {@code ::} for every one;
   *     none when it is not published
   * @param privatePort the port inside the container
   * @param publicPort the host port; none when it is not published
   * @param type {@code tcp} or {@code udp}
   */
  public record Port(
      @JsonProperty("IP") String ip,
      @JsonProperty("PrivatePort") int privatePort,
      @JsonProperty("PublicPort") Integer publicPort,
      @JsonProperty("Type") String type) {}

  /**
   * @param networks the networks a container is on, by name
   */
  public record NetworkSettings(@JsonProperty("Networks") Map<String, Network> networks) {

    public NetworkSettings {
      networks = networks == null ? Map.of() : networks;
    }
  }

  /**
   * @param ipAddress the container's address on the network; empty on the host's network
   */
  public record Network(@JsonProperty("IPAddress") String ipAddress) {}
}
