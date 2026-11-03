package dev.palazon.lvhyard.dashboard.services;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * @param name also the name of its Consul datacenter
 * @param services none while it is unreachable
 */
public record YardWorkstation(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean unreachable,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Service> services) {}
