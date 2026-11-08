package dev.palazon.lvhyard.dashboard.consul;

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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
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
        http.get()
            .uri("/v1/catalog/datacenters")
            .retrieve()
            .body(new ParameterizedTypeReference<>() {});
    return datacenters == null ? List.of() : datacenters;
  }

  /** Empty while the datacenter has no leader. */
  @Override
  public String leader() {
    String leader = http.get().uri("/v1/status/leader").retrieve().body(String.class);
    return leader == null ? "" : leader.strip().replace("\"", "");
  }

  /** The members of the agent's WAN pool: one server for each federated datacenter. */
  @Override
  public List<WanMember> wanMembers() {
    List<WanMember> members =
        http.get()
            .uri("/v1/agent/members?wan=1")
            .retrieve()
            .body(new ParameterizedTypeReference<>() {});
    return members == null ? List.of() : members;
  }

  /** compose.yaml sets it to the Workstation's LAN address. */
  @Override
  public String agentAddress() {
    return http.get().uri("/v1/agent/self").retrieve().body(AgentSelf.class).member().address();
  }

  /** Replaces the registration with the same ID. */
  @Override
  public void register(Registration registration) {
    http.put()
        .uri("/v1/agent/service/register")
        .contentType(MediaType.APPLICATION_JSON)
        .body(registration)
        .retrieve()
        .toBodilessEntity();
  }

  @Override
  public Map<String, AgentService> agentServices() {
    Map<String, AgentService> services =
        http.get().uri("/v1/agent/services").retrieve().body(new ParameterizedTypeReference<>() {});
    return services == null ? Map.of() : services;
  }

  @Override
  public Optional<AgentService> agentService(String id) {
    return http.get()
        .uri("/v1/agent/service/{id}", id)
        .exchange((request, response) ->
            response.getStatusCode() == HttpStatus.NOT_FOUND
                ? Optional.empty()
                : Optional.ofNullable(response.bodyTo(AgentService.class)));
  }

  @Override
  public void deregister(String id) {
    http.put().uri("/v1/agent/service/deregister/{id}", id).retrieve().toBodilessEntity();
  }

  @Override
  public List<String> serviceNames(String datacenter) {
    Map<String, List<String>> services =
        http.get()
            .uri(builder -> in(datacenter, builder.path("/v1/catalog/services")))
            .retrieve()
            .body(new ParameterizedTypeReference<>() {});
    return services == null ? List.of() : List.copyOf(services.keySet());
  }

  @Override
  public List<HealthEntry> health(String datacenter, String service) {
    List<HealthEntry> entries =
        http.get()
            .uri(builder -> in(datacenter, builder.path("/v1/health/service/{service}"), service))
            .retrieve()
            .body(new ParameterizedTypeReference<>() {});
    return entries == null ? List.of() : entries;
  }

  @Override
  public void putValue(String key, String value) {
    http.put()
        .uri(builder -> builder.path("/v1/kv/" + key).build())
        .contentType(MediaType.TEXT_PLAIN)
        .body(value)
        .retrieve()
        .toBodilessEntity();
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
    http.put()
        .uri("/v1/txn")
        .contentType(MediaType.APPLICATION_JSON)
        .body(writes)
        .retrieve()
        .toBodilessEntity();
  }

  @Override
  public Map<String, String> values(String datacenter, String prefix) {
    List<KeyValue> entries =
        http.get()
            .uri(builder ->
                in(datacenter, builder.path("/v1/kv/" + prefix).queryParam("recurse", true)))
            .exchange((request, response) ->
                response.getStatusCode() == HttpStatus.NOT_FOUND
                    ? List.of()
                    : response.bodyTo(new ParameterizedTypeReference<>() {}));
    Map<String, String> values = new TreeMap<>();
    if (entries != null) {
      entries.forEach(entry -> values.put(entry.key(), entry.value()));
    }
    return values;
  }

  /** A key that is not there is no error. */
  @Override
  public void deleteValue(String key) {
    http.delete()
        .uri(builder -> builder.path("/v1/kv/" + key).build())
        .retrieve()
        .toBodilessEntity();
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
}
