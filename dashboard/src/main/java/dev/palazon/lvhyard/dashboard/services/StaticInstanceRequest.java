package dev.palazon.lvhyard.dashboard.services;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A Static Instance to register, or what an edited one becomes.
 *
 * @param host the address it listens on; this Workstation's LAN address when left out
 * @param port the port it listens on
 * @param check how its Health is checked; a TCP check on its port when left out
 * @param tags words that say what it is for, without spaces or commas
 * @param meta notes about it, by key; keys use letters, digits, - and _, and do not start with lvh-yard- or
 *        consul-
 */
public record StaticInstanceRequest(
        String host,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Integer port,
        HealthCheck check,
        List<String> tags,
        Map<String, String> meta) {

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
