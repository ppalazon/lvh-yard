package dev.palazon.lvhyard.dashboard.reachability;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * What a Reachability test of one Service found, from this Workstation, at the moment it ran. It is never stored.
 *
 * @param workstation the Service's Home Workstation
 * @param service the Service's name
 * @param instances each Instance, as a straight connection to it found it, ordered as the Service's Instances are
 * @param routes each hostname this Workstation routes to the Service, as a request through the local Traefik
 *        found it
 * @param noRoute why this Workstation has no Route to the Service, so only its Instances were tested; {@code null},
 *        and left out of the JSON, when it has one
 * @param passed whether every check that ran reached the Service; a skipped Out-of-range Instance does not count
 */
public record Reachability(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String workstation,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String service,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<InstanceReachability> instances,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<RouteReachability> routes,
        @JsonInclude(JsonInclude.Include.NON_NULL) String noRoute,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean passed) {

    Reachability(String workstation, String service, List<InstanceReachability> instances,
            List<RouteReachability> routes, String noRoute) {
        this(workstation, service, instances, routes, noRoute,
                instances.stream().noneMatch(InstanceReachability::failed)
                        && routes.stream().allMatch(RouteReachability::reached));
    }
}
