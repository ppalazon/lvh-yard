package dev.palazon.lvhyard.dashboard.reachability;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/** {@code status} and {@code error} are left out of the JSON when null. */
public record RouteReachability(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String hostname,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean reached,
    @JsonInclude(JsonInclude.Include.NON_NULL) Integer status,
    @JsonInclude(JsonInclude.Include.NON_NULL) String error) {}
