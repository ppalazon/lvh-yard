package dev.palazon.lvhyard.dashboard.services;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A Service as the Yard sees it.
 *
 * @param workstation its Home Workstation
 * @param name its name, a single DNS label
 * @param instances its registered Instances, ordered by host and port
 */
public record Service(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String workstation,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Instance> instances) {
}
