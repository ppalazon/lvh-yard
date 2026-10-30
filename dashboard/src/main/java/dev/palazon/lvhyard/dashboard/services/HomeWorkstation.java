package dev.palazon.lvhyard.dashboard.services;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A Workstation of the Yard, with the Services it is the Home Workstation of.
 *
 * @param name its name, which is also its Consul datacenter's
 * @param unreachable whether its datacenter did not answer, so its Services are not known for now
 * @param services its Services, ordered by name; none while it is unreachable
 */
public record HomeWorkstation(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean unreachable,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Service> services) {
}
