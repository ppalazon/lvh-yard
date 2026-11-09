package dev.palazon.lvhyard.dashboard.reachability;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.palazon.lvhyard.dashboard.services.Alias;
import dev.palazon.lvhyard.dashboard.services.Routing;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Never stored. {@code noRoute}, {@code routing} and {@code alias} are left out of the JSON when
 * null. {@code passed} does not count a skipped Out-of-range Instance.
 *
 * @param routing where the Route on this Workstation sends requests now, or null without a Route
 * @param alias the Alias whose Remote Service gets the requests, while {@code routing} uses an
 *     Alias source
 */
public record Reachability(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String workstation,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String service,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<InstanceReachability> instances,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<RouteReachability> routes,
    @JsonInclude(JsonInclude.Include.NON_NULL) String noRoute,
    @JsonInclude(JsonInclude.Include.NON_NULL) Routing routing,
    @JsonInclude(JsonInclude.Include.NON_NULL) Alias alias,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean passed) {

  Reachability(
      String workstation,
      String service,
      List<InstanceReachability> instances,
      List<RouteReachability> routes,
      String noRoute,
      Routing routing,
      Alias alias) {
    this(
        workstation,
        service,
        instances,
        routes,
        noRoute,
        routing,
        alias,
        instances.stream().noneMatch(InstanceReachability::failed)
            && routes.stream().allMatch(RouteReachability::reached));
  }
}
