package cc.lvhy.yard.dashboard.consul;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Supplier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriBuilder;

/**
 * Consul's HTTP API on the local agent. A read can name another datacenter, which the agent
 * forwards over WAN federation.
 */
@Component
public class ConsulAgent implements Consul {

  private final RestClient http;

  /** For blocking queries, which wait far longer than an answer may take. */
  private final HttpClient watch;

  private final URI url;

  public ConsulAgent(ConsulSettings settings) {
    JdkClientHttpRequestFactory requests =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(settings.answerTimeout()).build());
    requests.setReadTimeout(settings.answerTimeout());
    this.http =
        RestClient.builder().baseUrl(settings.url().toString()).requestFactory(requests).build();
    this.watch = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    this.url = settings.url();
  }

  @Override
  public long awaitChange(Watched watched, long index, Duration wait) {
    String path =
        switch (watched) {
          case HEALTH -> "/v1/health/state/any";
          case KV -> "/v1/kv/lvh-yard/?recurse=true";
          case SERVICES -> "/v1/catalog/services";
        };
    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(
                URI.create(
                    url
                        + path
                        + (path.contains("?") ? "&" : "?")
                        + "index="
                        + index
                        + "&wait="
                        + wait.toSeconds()
                        + "s"))
            .timeout(wait.plusSeconds(5))
            .GET()
            .build();
    HttpResponse<Void> response;
    try {
      response = watch.send(request, HttpResponse.BodyHandlers.discarding());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Stopped while waiting for Consul", e);
    }
    if (response.statusCode() != 200 && response.statusCode() != 404) {
      throw new IllegalStateException("Consul answered with HTTP status " + response.statusCode());
    }
    try {
      return response.headers().firstValue("X-Consul-Index").map(Long::parseLong).orElse(index);
    } catch (NumberFormatException e) {
      return index;
    }
  }

  /** Every federated datacenter, answering or not. */
  @Override
  public List<String> datacenters() {
    List<String> datacenters =
        call(() ->
            http.get()
                .uri("/v1/catalog/datacenters")
                .retrieve()
                .body(new ParameterizedTypeReference<List<String>>() {}));
    return datacenters == null ? List.of() : datacenters;
  }

  /** Empty while the datacenter has no leader. */
  @Override
  public String leader(String datacenter) {
    String leader =
        call(() ->
            http.get()
                .uri(builder -> in(datacenter, builder.path("/v1/status/leader")))
                .retrieve()
                .body(String.class));
    return leader == null ? "" : leader.strip().replace("\"", "");
  }

  /** The members of the agent's WAN pool: one server for each federated datacenter. */
  @Override
  public List<WanMember> wanMembers() {
    List<WireWanMember> members =
        call(() ->
            http.get()
                .uri("/v1/agent/members?wan=1")
                .retrieve()
                .body(new ParameterizedTypeReference<List<WireWanMember>>() {}));
    return members == null ? List.of() : members.stream().map(WireWanMember::toPort).toList();
  }

  /** compose.yaml sets it to the Workstation's LAN address. */
  @Override
  public String agentAddress() {
    AgentSelf self = call(() -> http.get().uri("/v1/agent/self").retrieve().body(AgentSelf.class));
    if (self == null) {
      throw new IllegalStateException("Consul answered /v1/agent/self without a body.");
    }
    return self.member().address();
  }

  /** Replaces the registration with the same ID. */
  @Override
  public void register(Registration registration) {
    run(() ->
        http.put()
            .uri("/v1/agent/service/register")
            .contentType(MediaType.APPLICATION_JSON)
            .body(WireRegistration.of(registration))
            .retrieve()
            .toBodilessEntity());
  }

  @Override
  public Map<String, AgentService> agentServices() {
    Map<String, WireAgentService> services =
        call(() ->
            http.get()
                .uri("/v1/agent/services")
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, WireAgentService>>() {}));
    Map<String, AgentService> result = new LinkedHashMap<>();
    if (services != null) {
      services.forEach((id, service) -> result.put(id, service.toPort()));
    }
    return result;
  }

  @Override
  public Optional<AgentService> agentService(String id) {
    return call(() ->
        http.get()
            .uri("/v1/agent/service/{id}", id)
            .exchange((request, response) ->
                response.getStatusCode() == HttpStatus.NOT_FOUND
                    ? Optional.empty()
                    : Optional.ofNullable(response.bodyTo(WireAgentService.class))
                        .map(WireAgentService::toPort)));
  }

  @Override
  public void deregister(String id) {
    run(() ->
        http.put().uri("/v1/agent/service/deregister/{id}", id).retrieve().toBodilessEntity());
  }

  @Override
  public List<String> serviceNames(String datacenter) {
    Map<String, List<String>> services =
        call(() ->
            http.get()
                .uri(builder -> in(datacenter, builder.path("/v1/catalog/services")))
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, List<String>>>() {}));
    return services == null ? List.of() : List.copyOf(services.keySet());
  }

  @Override
  public List<HealthEntry> health(String datacenter, String service) {
    List<WireHealthEntry> entries =
        call(() ->
            http.get()
                .uri(builder ->
                    in(datacenter, builder.path("/v1/health/service/{service}"), service))
                .retrieve()
                .body(new ParameterizedTypeReference<List<WireHealthEntry>>() {}));
    return entries == null ? List.of() : entries.stream().map(WireHealthEntry::toPort).toList();
  }

  @Override
  public void putValue(String key, String value) {
    run(() ->
        http.put()
            .uri(builder -> builder.path("/v1/kv/" + key).build())
            .contentType(MediaType.TEXT_PLAIN)
            .body(value)
            .retrieve()
            .toBodilessEntity());
  }

  /**
   * Writes each value at its key in one Consul transaction, so Consul takes all or none.
   *
   * @param values at most {@link #TRANSACTION_SIZE}
   */
  @Override
  public void putValues(Map<String, String> values) {
    if (values.size() > TRANSACTION_SIZE) {
      throw new IllegalArgumentException(
          "One Consul transaction takes at most " + TRANSACTION_SIZE + " writes.");
    }
    if (values.isEmpty()) {
      return;
    }
    List<Map<String, Object>> writes =
        values.entrySet().stream()
            .<Map<String, Object>>map(entry ->
                Map.of(
                    "KV",
                    Map.of(
                        "Verb",
                        "set",
                        "Key",
                        entry.getKey(),
                        "Value",
                        Base64.getEncoder()
                            .encodeToString(entry.getValue().getBytes(StandardCharsets.UTF_8)))))
            .toList();
    run(() ->
        http.put()
            .uri("/v1/txn")
            .contentType(MediaType.APPLICATION_JSON)
            .body(writes)
            .retrieve()
            .toBodilessEntity());
  }

  @Override
  public Map<String, String> values(String datacenter, String prefix) {
    // retrieve, not exchange: a 5xx must become a ConsulServerException. Consul answers a
    // datacenter it has no path to with a plain text body, which no JSON reader takes.
    List<KeyValue> entries =
        call(() -> {
          try {
            return http.get()
                .uri(builder ->
                    in(datacenter, builder.path("/v1/kv/" + prefix).queryParam("recurse", true)))
                .retrieve()
                .body(new ParameterizedTypeReference<List<KeyValue>>() {});
          } catch (HttpClientErrorException.NotFound e) {
            return List.of();
          }
        });
    Map<String, String> values = new TreeMap<>();
    if (entries != null) {
      entries.forEach(entry -> values.put(entry.key(), entry.value()));
    }
    return values;
  }

  /** A key that is not there is no error. */
  @Override
  public void deleteValue(String key) {
    run(() ->
        http.delete()
            .uri(builder -> builder.path("/v1/kv/" + key).build())
            .retrieve()
            .toBodilessEntity());
  }

  /**
   * Asks Consul, and gives its failures as the port's own exceptions, with the message of the HTTP
   * client.
   */
  private static <T> T call(Supplier<T> request) {
    try {
      return request.get();
    } catch (ResourceAccessException e) {
      throw new ConsulUnreachableException(e.getMessage(), e);
    } catch (HttpServerErrorException e) {
      throw new ConsulServerException(e.getMessage(), e);
    } catch (RestClientException e) {
      throw new ConsulException(e.getMessage(), e);
    }
  }

  private static void run(Runnable request) {
    call(() -> {
      request.run();
      return null;
    });
  }

  /** {@code null} asks the agent's own datacenter. */
  private static URI in(String datacenter, UriBuilder builder, Object... variables) {
    return (datacenter == null ? builder : builder.queryParam("dc", datacenter)).build(variables);
  }

  /** Consul gives the value in base64, or null when empty. */
  record KeyValue(@JsonProperty("Key") String key, @JsonProperty("Value") String base64) {

    String value() {
      return base64 == null
          ? ""
          : new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
    }
  }

  record AgentSelf(@JsonProperty("Member") Member member) {}

  record Member(@JsonProperty("Addr") String address) {}

  // The JSON of Consul's HTTP API, with its field names. Nothing outside this class sees it.

  record WireWanMember(
      @JsonProperty("Addr") String address,
      @JsonProperty("Port") int port,
      @JsonProperty("Status") Integer status,
      @JsonProperty("Tags") Map<String, String> tags) {

    WanMember toPort() {
      return new WanMember(
          address, port, status == null ? SerfStatus.UNKNOWN : SerfStatus.of(status), tags);
    }
  }

  record WireAgentService(
      @JsonProperty("ID") String id,
      @JsonProperty("Service") String name,
      @JsonProperty("Address") String address,
      @JsonProperty("Port") Integer port,
      @JsonProperty("Tags") List<String> tags,
      @JsonProperty("Meta") Map<String, String> meta) {

    AgentService toPort() {
      return new AgentService(id, name, address, port, tags, meta);
    }
  }

  record WireCheckStatus(
      @JsonProperty("Name") String name,
      @JsonProperty("Status") String status,
      @JsonProperty("Output") String output,
      @JsonProperty("ServiceID") String serviceId,
      @JsonProperty("Type") String type,
      @JsonProperty("Interval") String interval,
      @JsonProperty("Timeout") String timeout) {

    CheckStatus toPort() {
      return new CheckStatus(name, status, output, serviceId, type, interval, timeout);
    }
  }

  record WireHealthEntry(
      @JsonProperty("Service") WireAgentService service,
      @JsonProperty("Checks") List<WireCheckStatus> checks) {

    HealthEntry toPort() {
      return new HealthEntry(
          service.toPort(),
          checks == null ? List.of() : checks.stream().map(WireCheckStatus::toPort).toList());
    }
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  record WireCheck(
      @JsonProperty("Name") String name,
      @JsonProperty("HTTP") String http,
      @JsonProperty("TCP") String tcp,
      @JsonProperty("Interval") String interval,
      @JsonProperty("Timeout") String timeout) {}

  record WireRegistration(
      @JsonProperty("ID") String id,
      @JsonProperty("Name") String name,
      @JsonProperty("Address") String address,
      @JsonProperty("Port") int port,
      @JsonProperty("Tags") List<String> tags,
      @JsonProperty("Meta") Map<String, String> meta,
      @JsonProperty("Check") WireCheck check) {

    static WireRegistration of(Registration registration) {
      Check check = registration.check();
      return new WireRegistration(
          registration.id(),
          registration.name(),
          registration.address(),
          registration.port(),
          registration.tags(),
          registration.meta(),
          check == null
              ? null
              : new WireCheck(
                  check.name(), check.http(), check.tcp(), check.interval(), check.timeout()));
    }
  }
}
