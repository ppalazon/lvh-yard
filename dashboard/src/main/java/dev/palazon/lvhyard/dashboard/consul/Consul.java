package dev.palazon.lvhyard.dashboard.consul;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
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
 * Only the calls LVH Yard makes, with Consul's own names. A read can name another datacenter, which
 * the agent forwards over WAN federation. {@code null} names the agent's own.
 */
@Component
public class Consul {

  /**
   * The name and ID of the Consul server's own entry in its catalog, which is no Instance of the
   * Yard.
   */
  private static final String CONSUL_SERVICE = "consul";

  private final RestClient http;

  public Consul(ConsulSettings settings) {
    JdkClientHttpRequestFactory requests =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(settings.answerTimeout()).build());
    requests.setReadTimeout(settings.answerTimeout());
    this.http =
        RestClient.builder().baseUrl(settings.url().toString()).requestFactory(requests).build();
  }

  /** Every federated datacenter, answering or not. */
  public List<String> datacenters() {
    List<String> datacenters =
        http.get()
            .uri("/v1/catalog/datacenters")
            .retrieve()
            .body(new ParameterizedTypeReference<>() {});
    return datacenters == null ? List.of() : datacenters;
  }

  /** Empty while the datacenter has no leader. */
  public String leader() {
    String leader = http.get().uri("/v1/status/leader").retrieve().body(String.class);
    return leader == null ? "" : leader.strip().replace("\"", "");
  }

  /** The members of the agent's WAN pool: one server for each federated datacenter. */
  public List<WanMember> wanMembers() {
    List<WanMember> members =
        http.get()
            .uri("/v1/agent/members?wan=1")
            .retrieve()
            .body(new ParameterizedTypeReference<>() {});
    return members == null ? List.of() : members;
  }

  /** compose.yaml sets it to the Workstation's LAN address. */
  public String agentAddress() {
    return http.get().uri("/v1/agent/self").retrieve().body(AgentSelf.class).member().address();
  }

  /** Replaces the registration with the same ID. */
  public void register(Registration registration) {
    http.put()
        .uri("/v1/agent/service/register")
        .contentType(MediaType.APPLICATION_JSON)
        .body(registration)
        .retrieve()
        .toBodilessEntity();
  }

  public Map<String, AgentService> agentServices() {
    Map<String, AgentService> services =
        http.get().uri("/v1/agent/services").retrieve().body(new ParameterizedTypeReference<>() {});
    return services == null ? Map.of() : services;
  }

  public Optional<AgentService> agentService(String id) {
    return http.get()
        .uri("/v1/agent/service/{id}", id)
        .exchange((request, response) ->
            response.getStatusCode() == HttpStatus.NOT_FOUND
                ? Optional.empty()
                : Optional.ofNullable(response.bodyTo(AgentService.class)));
  }

  public void deregister(String id) {
    http.put().uri("/v1/agent/service/deregister/{id}", id).retrieve().toBodilessEntity();
  }

  public List<String> serviceNames(String datacenter) {
    Map<String, List<String>> services =
        http.get()
            .uri(builder -> in(datacenter, builder.path("/v1/catalog/services")))
            .retrieve()
            .body(new ParameterizedTypeReference<>() {});
    return services == null ? List.of() : List.copyOf(services.keySet());
  }

  /** Each registration comes with its own checks and its node's. */
  public List<HealthEntry> health(String service) {
    return health(null, service);
  }

  public List<HealthEntry> health(String datacenter, String service) {
    List<HealthEntry> entries =
        http.get()
            .uri(builder -> in(datacenter, builder.path("/v1/health/service/{service}"), service))
            .retrieve()
            .body(new ParameterizedTypeReference<>() {});
    return entries == null ? List.of() : entries;
  }

  public void putValue(String key, String value) {
    http.put()
        .uri(builder -> builder.path("/v1/kv/" + key).build())
        .contentType(MediaType.TEXT_PLAIN)
        .body(value)
        .retrieve()
        .toBodilessEntity();
  }

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
  public void deleteValue(String key) {
    http.delete()
        .uri(builder -> builder.path("/v1/kv/" + key).build())
        .retrieve()
        .toBodilessEntity();
  }

  public static boolean isServerItself(AgentService registration) {
    return registration.name().equals(CONSUL_SERVICE) && registration.id().equals(CONSUL_SERVICE);
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

  /** {@code status} is alive, leaving, left or failed. The {@code dc} tag is its datacenter. */
  public record WanMember(
      @JsonProperty("Addr") String address,
      @JsonProperty("Port") int port,
      @JsonProperty("Status") SerfStatus status,
      @JsonProperty("Tags") Map<String, String> tags) {

    public WanMember {
      status = status == null ? SerfStatus.UNKNOWN : status;
      tags = tags == null ? Map.of() : tags;
    }

    /** The datacenter, which is its Workstation's name. */
    public String datacenter() {
      return tags.getOrDefault("dc", "");
    }

    public boolean alive() {
      return status == SerfStatus.ALIVE;
    }
  }

  /** Consul sends it as Serf's code. */
  public enum SerfStatus {
    UNKNOWN,
    ALIVE,
    LEAVING,
    LEFT,
    FAILED;

    /** 1 alive, 2 leaving, 3 left, 4 failed. */
    @JsonCreator
    public static SerfStatus of(int code) {
      SerfStatus[] all = values();
      return code > 0 && code < all.length ? all[code] : UNKNOWN;
    }

    @Override
    public String toString() {
      return name().toLowerCase();
    }
  }

  record AgentSelf(@JsonProperty("Member") Member member) {}

  record Member(@JsonProperty("Addr") String address) {}

  public record Registration(
      @JsonProperty("ID") String id,
      @JsonProperty("Name") String name,
      @JsonProperty("Address") String address,
      @JsonProperty("Port") int port,
      @JsonProperty("Tags") List<String> tags,
      @JsonProperty("Meta") Map<String, String> meta,
      @JsonProperty("Check") Check check) {}

  /** Consul starts every check as critical until it first passes. */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Check(
      @JsonProperty("Name") String name,
      @JsonProperty("HTTP") String http,
      @JsonProperty("TCP") String tcp,
      @JsonProperty("Interval") String interval,
      @JsonProperty("Timeout") String timeout) {}

  /** Consul leaves out a port of 0 and empty meta. */
  public record AgentService(
      @JsonProperty("ID") String id,
      @JsonProperty("Service") String name,
      @JsonProperty("Address") String address,
      @JsonProperty("Port") Integer port,
      @JsonProperty("Tags") List<String> tags,
      @JsonProperty("Meta") Map<String, String> meta) {

    public AgentService {
      port = port == null ? 0 : port;
      tags = tags == null ? List.of() : tags;
      meta = meta == null ? Map.of() : meta;
    }
  }

  public record HealthEntry(
      @JsonProperty("Service") AgentService service,
      @JsonProperty("Checks") List<CheckStatus> checks) {}

  /**
   * Maintenance counts as critical. A node's check has no service ID, type, interval or timeout.
   * Consul gives durations in Go format, such as {@code 1m30s}.
   */
  public record CheckStatus(
      @JsonProperty("Name") String name,
      @JsonProperty("Status") String status,
      @JsonProperty("Output") String output,
      @JsonProperty("ServiceID") String serviceId,
      @JsonProperty("Type") String type,
      @JsonProperty("Interval") String interval,
      @JsonProperty("Timeout") String timeout) {}
}
