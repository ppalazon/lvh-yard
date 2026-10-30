package dev.palazon.lvhyard.dashboard.reachability;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Whether this Workstation could connect straight to one Instance.
 *
 * @param id the Instance's registration ID
 * @param address {@code host:port}, where the test connected to
 * @param reached whether a TCP connection was made
 * @param error why no connection was made, such as {@code Connection refused}; {@code null}, and left out of the
 *        JSON, when it was
 */
public record InstanceReachability(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String address,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean reached,
        @JsonInclude(JsonInclude.Include.NON_NULL) String error) {
}
