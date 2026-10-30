package dev.palazon.lvhyard.dashboard.services;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One running copy of a Service.
 *
 * @param id the Instance's registration ID, which the Service's Instances path names it by
 * @param host the address it listens on
 * @param port the port it listens on
 * @param health the result of its checks
 * @param check how its Health is checked
 * @param tags words that say what it is for
 * @param meta notes about it, by key
 * @param checks the last result of each check its Health comes from: its own and its Workstation's
 */
public record Instance(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String host,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int port,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Health health,
        @JsonInclude(JsonInclude.Include.NON_NULL) HealthCheck check,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> tags,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Map<String, String> meta,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<CheckResult> checks) {

    /** {@code host:port}, where the Instance is reached. */
    public String address() {
        return address(host, port);
    }

    /** {@code host:port}, with an IPv6 host in brackets so its colons are not read as the port's. */
    static String address(String host, int port) {
        return (host.contains(":") ? "[" + host + "]" : host) + ":" + port;
    }
}
