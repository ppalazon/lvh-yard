package cc.lvhy.yard.dashboard.services;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * @param on whether the Route uses only the first source in the order with a passing Instance
 * @param sources in order
 * @param routing where the Route on this Workstation sends requests now, or {@code null} when it
 *     has no Route
 */
public record InstancePriority(
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description =
                "Whether the Route uses only the first Instance Source in the order with a passing"
                    + " Instance")
        boolean on,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<InstanceSource> sources,
    Routing routing) {

  static InstancePriority of(Service service) {
    return new InstancePriority(service.instancePriority(), service.sources(), service.routing());
  }
}
