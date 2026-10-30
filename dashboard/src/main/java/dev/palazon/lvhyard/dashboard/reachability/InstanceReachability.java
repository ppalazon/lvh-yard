package dev.palazon.lvhyard.dashboard.reachability;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Whether this Workstation could connect straight to one Instance.
 *
 * @param id the Instance's registration ID
 * @param address {@code host:port}, where the test connected to
 * @param reached whether a TCP connection was made
 * @param outOfRange whether it is an Out-of-range Instance, at an address only its Home Workstation can connect to,
 *        so it was skipped: a connection from here would reach something else. Not a failure; left out of the JSON
 *        when false
 * @param error why no connection was made, such as {@code Connection refused}; {@code null}, and left out of the
 *        JSON, when it was, or when it was skipped
 */
public record InstanceReachability(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String address,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean reached,
        @JsonInclude(JsonInclude.Include.NON_DEFAULT) boolean outOfRange,
        @JsonInclude(JsonInclude.Include.NON_NULL) String error) {

    /** Whether the check failed: it ran, and no connection was made. */
    public boolean failed() {
        return !reached && !outOfRange;
    }
}
