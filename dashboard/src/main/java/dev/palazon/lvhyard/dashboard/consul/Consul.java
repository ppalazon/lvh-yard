package dev.palazon.lvhyard.dashboard.consul;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * This Workstation's Consul agent, through its HTTP API. Only the calls LVH Yard makes, with Consul's own names; it
 * knows nothing of the Yard's rules.
 */
@Component
public class Consul {

    /** The Consul server's own entry in its catalog, which is no Service of the Yard. */
    public static final String CONSUL_SERVICE = "consul";

    private final RestClient http;

    Consul(ConsulSettings settings) {
        this.http = RestClient.builder().baseUrl(settings.url().toString()).build();
    }

    /** The address the agent binds to, which compose.yaml sets to the Workstation's LAN address. */
    public String agentAddress() {
        return http.get().uri("/v1/agent/self").retrieve().body(AgentSelf.class).member().address();
    }

    /** Adds a registration to the agent, or replaces the one with the same ID. */
    public void register(Registration registration) {
        http.put().uri("/v1/agent/service/register").contentType(MediaType.APPLICATION_JSON).body(registration)
                .retrieve().toBodilessEntity();
    }

    /** Every registration on the agent, by ID. */
    public Map<String, AgentService> agentServices() {
        Map<String, AgentService> services = http.get().uri("/v1/agent/services").retrieve()
                .body(new ParameterizedTypeReference<>() { });
        return services == null ? Map.of() : services;
    }

    /** The agent's registration with this ID, if it has one. */
    public Optional<AgentService> agentService(String id) {
        return http.get().uri("/v1/agent/service/{id}", id).exchange((request, response) ->
                response.getStatusCode() == HttpStatus.NOT_FOUND
                        ? Optional.empty()
                        : Optional.ofNullable(response.bodyTo(AgentService.class)));
    }

    public void deregister(String id) {
        http.put().uri("/v1/agent/service/deregister/{id}", id).retrieve().toBodilessEntity();
    }

    /** The names of the Services registered in the agent's datacenter. */
    public List<String> serviceNames() {
        Map<String, List<String>> services = http.get().uri("/v1/catalog/services").retrieve()
                .body(new ParameterizedTypeReference<>() { });
        return services == null ? List.of() : List.copyOf(services.keySet());
    }

    /** Every registration of a Service in the agent's datacenter, each with its checks and its node's. */
    public List<HealthEntry> health(String service) {
        List<HealthEntry> entries = http.get().uri("/v1/health/service/{service}", service).retrieve()
                .body(new ParameterizedTypeReference<>() { });
        return entries == null ? List.of() : entries;
    }

    /** Stores {@code value} at {@code key} in the datacenter's KV store, replacing what was there. */
    public void putValue(String key, String value) {
        http.put().uri(builder -> builder.path("/v1/kv/" + key).build()).contentType(MediaType.TEXT_PLAIN)
                .body(value).retrieve().toBodilessEntity();
    }

    /** Every value in the KV store whose key starts with {@code prefix}, by key. */
    public Map<String, String> values(String prefix) {
        List<KeyValue> entries = http.get().uri(builder -> builder.path("/v1/kv/" + prefix)
                .queryParam("recurse", true).build()).exchange((request, response) ->
                        response.getStatusCode() == HttpStatus.NOT_FOUND
                                ? List.of()
                                : response.bodyTo(new ParameterizedTypeReference<>() { }));
        Map<String, String> values = new TreeMap<>();
        if (entries != null) {
            entries.forEach(entry -> values.put(entry.key(), entry.value()));
        }
        return values;
    }

    /** Removes {@code key} from the KV store; a key that is not there is no error. */
    public void deleteValue(String key) {
        http.delete().uri(builder -> builder.path("/v1/kv/" + key).build()).retrieve().toBodilessEntity();
    }

    /** A KV entry as Consul gives it back: the value in base64, or null when empty. */
    record KeyValue(@JsonProperty("Key") String key, @JsonProperty("Value") String base64) {

        /** The value as text. */
        String value() {
            return base64 == null ? "" : new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
        }
    }

    record AgentSelf(@JsonProperty("Member") Member member) {
    }

    record Member(@JsonProperty("Addr") String address) {
    }

    /** What the agent is given to register a Service's Instance. */
    public record Registration(
            @JsonProperty("ID") String id,
            @JsonProperty("Name") String name,
            @JsonProperty("Address") String address,
            @JsonProperty("Port") int port,
            @JsonProperty("Meta") Map<String, String> meta,
            @JsonProperty("Check") Check check) {
    }

    /** A TCP check. Consul starts every check as critical until it first passes. */
    public record Check(
            @JsonProperty("TCP") String tcp,
            @JsonProperty("Interval") String interval,
            @JsonProperty("Timeout") String timeout) {
    }

    /** A registration as the agent and the catalog give it back. Consul leaves out a port of 0 and empty meta. */
    public record AgentService(
            @JsonProperty("ID") String id,
            @JsonProperty("Service") String name,
            @JsonProperty("Address") String address,
            @JsonProperty("Port") Integer port,
            @JsonProperty("Meta") Map<String, String> meta) {

        public AgentService {
            port = port == null ? 0 : port;
            meta = meta == null ? Map.of() : meta;
        }
    }

    public record HealthEntry(
            @JsonProperty("Service") AgentService service,
            @JsonProperty("Checks") List<CheckStatus> checks) {
    }

    /** A check's last result: passing, warning or critical (maintenance is critical too). */
    public record CheckStatus(
            @JsonProperty("Status") String status,
            @JsonProperty("Output") String output) {
    }
}
