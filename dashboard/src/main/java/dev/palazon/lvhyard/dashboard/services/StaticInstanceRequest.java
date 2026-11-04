package dev.palazon.lvhyard.dashboard.services;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Defaults: this Workstation's LAN address, and a TCP check on its port. Meta keys do not start
 * with lvh-yard- or consul-.
 *
 * @param publish {@code false} registers a new Service without publishing it. An edit ignores it.
 */
public record StaticInstanceRequest(
    String host,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Integer port,
    HealthCheck check,
    List<String> tags,
    Map<String, String> meta,
    @Schema(
            description =
                "If the Service is new, false leaves it Unpublished. Otherwise it gets a"
                    + " Publication with Local Scope. An existing Service keeps its Publication, or"
                    + " stays Unpublished. An edit ignores this field.")
        Boolean publish) {

  /** Left-out tags and meta are none; a meta key with no value has an empty one. */
  public StaticInstanceRequest {
    tags = tags == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(tags));
    Map<String, String> values = new TreeMap<>();
    if (meta != null) {
      meta.forEach((key, value) -> values.put(key, value == null ? "" : value));
    }
    meta = Collections.unmodifiableMap(values);
  }
}
