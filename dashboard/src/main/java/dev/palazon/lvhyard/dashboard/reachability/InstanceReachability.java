package dev.palazon.lvhyard.dashboard.reachability;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * An Out-of-range Instance is skipped, not failed, because a connection from here would reach
 * something else. {@code outOfRange} and {@code error} are left out of the JSON when false or null.
 */
public record InstanceReachability(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String id,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String address,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean reached,
    @JsonInclude(JsonInclude.Include.NON_DEFAULT) boolean outOfRange,
    @JsonInclude(JsonInclude.Include.NON_NULL) String error) {

  /** A skipped Instance has not failed. */
  public boolean failed() {
    return !reached && !outOfRange;
  }
}
