package cc.lvhy.yard.dashboard.services;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Its own datacenter always answers.
 *
 * @param name also the name of its Consul datacenter
 */
public record HomeWorkstation(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Service> services) {}
