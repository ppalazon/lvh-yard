package cc.lvhy.yard.dashboard.docker;

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
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MappingIterator;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** Docker's Engine API, through its socket or TCP address. */
@Component
public class DockerEngine implements Docker {

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

  DockerEngine(DockerSettings settings) {
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

  @Override
  public void ping() {
    try (Response response = http.execute(get("/_ping"))) {
      checkOk(response);
    }
  }

  @Override
  public Version version() {
    try (Response response = http.execute(get("/version"))) {
      checkOk(response);
      return JSON.readValue(response.getBody(), Version.class);
    } catch (JacksonException e) {
      throw new IllegalStateException("Docker answered a version that cannot be read", e);
    }
  }

  @Override
  public List<Container> runningContainers() {
    return containers("/containers/json");
  }

  @Override
  public List<Container> containers() {
    return containers("/containers/json?all=1");
  }

  private List<Container> containers(String path) {
    try (Response response = http.execute(get(path))) {
      checkOk(response);
      return JSON.readValue(response.getBody(), new TypeReference<List<Container>>() {});
    } catch (JacksonException e) {
      throw new IllegalStateException("Docker answered containers that cannot be read", e);
    }
  }

  @Override
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
      return new EngineEvents(response, JSON.readerFor(Event.class).readValues(response.getBody()));
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

  private static final class EngineEvents implements ContainerEvents {

    private final Response response;

    private final MappingIterator<Event> events;

    private EngineEvents(Response response, MappingIterator<Event> events) {
      this.response = response;
      this.events = events;
    }

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
}
