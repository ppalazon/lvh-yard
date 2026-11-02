package dev.palazon.lvhyard.dashboard.services;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * This Workstation, as the Home Workstation of the Services it can change. Its own datacenter
 * always answers.
 *
 * @param name its name, which is also its Consul datacenter's
 * @param services its Services, ordered by name
 */
public record HomeWorkstation(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Service> services) {}
