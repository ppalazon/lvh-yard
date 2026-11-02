package dev.palazon.lvhyard.dashboard.services;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One check's last result, for an Instance's own check or its Workstation's.
 *
 * @param name what the check does, such as {@code HTTP GET /health}
 * @param health its last result
 * @param output what it last saw, such as the answer to an HTTP check; empty before it first runs
 */
public record CheckResult(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Health health,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String output) {}
