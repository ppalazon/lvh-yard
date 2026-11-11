package cc.lvhy.yard.dashboard.services;

import io.swagger.v3.oas.annotations.media.Schema;

/** A Service to create, with nothing but its name. */
public record NewServiceRequest(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "One DNS label")
        String name) {}
