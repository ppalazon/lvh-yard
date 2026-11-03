package dev.palazon.lvhyard.dashboard.reachability;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Never stored. {@code noRoute} is left out of the JSON when null. {@code passed} does not count a
 * skipped Out-of-range Instance.
 */
public record Reachability(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String workstation,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String service,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<InstanceReachability> instances,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<RouteReachability> routes,
    @JsonInclude(JsonInclude.Include.NON_NULL) String noRoute,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean passed) {

  Reachability(
      String workstation,
      String service,
      List<InstanceReachability> instances,
      List<RouteReachability> routes,
      String noRoute) {
    this(
        workstation,
        service,
        instances,
        routes,
        noRoute,
        instances.stream().noneMatch(InstanceReachability::failed)
            && routes.stream().allMatch(RouteReachability::reached));
  }
}
