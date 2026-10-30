package dev.palazon.lvhyard.dashboard.services;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The Remote Service an Alias is to name.
 *
 * @param workstation its Home Workstation, another one of the Yard
 * @param service its name
 */
public record AliasRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String workstation,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String service) {
}
