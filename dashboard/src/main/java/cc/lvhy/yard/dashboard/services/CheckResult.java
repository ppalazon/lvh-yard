package cc.lvhy.yard.dashboard.services;

import io.swagger.v3.oas.annotations.media.Schema;

/** {@code output} is empty before the check first runs. */
public record CheckResult(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Health health,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String output) {}
