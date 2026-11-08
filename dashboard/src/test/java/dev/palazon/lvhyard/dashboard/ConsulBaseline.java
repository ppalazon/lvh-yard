package dev.palazon.lvhyard.dashboard;

import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/**
 * A Consul's KV and registrations just after the first setup, so that each test starts from them
 * and not from what earlier tests left.
 */
record ConsulBaseline(Map<String, String> values, Set<String> registrations) {

  /** What the Consul that {@code consul} reaches holds now. */
  static ConsulBaseline of(RestClient consul) {
    return new ConsulBaseline(values(consul), registrations(consul));
  }

  /**
   * Puts the Consul that {@code consul} reaches back to this baseline. KV goes first, so the
   * dashboard finds no Container Binding to register again after its Instance is removed.
   */
  void restore(RestClient consul) {
    Map<String, String> now = values(consul);
    now.keySet().stream()
        .filter(key -> !values.containsKey(key))
        .forEach(key -> consul.delete().uri("/v1/kv/{key}", key).retrieve().toBodilessEntity());
    values.forEach((key, value) -> {
      if (!Objects.equals(now.get(key), value)) {
        consul
            .put()
            .uri("/v1/kv/{key}", key)
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(Base64.getDecoder().decode(value))
            .retrieve()
            .toBodilessEntity();
      }
    });
    registrations(consul).stream()
        .filter(id -> !registrations.contains(id))
        .forEach(id ->
            consul
                .put()
                .uri("/v1/agent/service/deregister/{id}", id)
                .retrieve()
                .toBodilessEntity());
  }

  /** Each key with its value in Base64, as Consul gives it. An empty value is "". */
  private static Map<String, String> values(RestClient consul) {
    JsonNode entries =
        consul
            .get()
            .uri("/v1/kv/?recurse")
            .exchange((request, response) ->
                response.getStatusCode() == HttpStatus.NOT_FOUND
                    ? null
                    : response.bodyTo(JsonNode.class));
    Map<String, String> values = new HashMap<>();
    if (entries != null) {
      entries.forEach(entry -> {
        JsonNode value = entry.get("Value");
        values.put(
            entry.get("Key").asString(), value == null || value.isNull() ? "" : value.asString());
      });
    }
    return values;
  }

  private static Set<String> registrations(RestClient consul) {
    JsonNode services = consul.get().uri("/v1/agent/services").retrieve().body(JsonNode.class);
    return new HashSet<>(services.propertyNames());
  }
}
