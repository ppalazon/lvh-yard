package dev.palazon.lvhyard.dashboard.services;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * @param workstation another Workstation of the Yard
 * @param create true creates a missing Service and publishes it with Local Scope
 */
public record AliasRequest(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String workstation,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String service,
    @Schema(
            description =
                "true creates the Service when it does not exist, and publishes it with Local"
                    + " Scope. An existing Service keeps its Publication.")
        Boolean create) {}
