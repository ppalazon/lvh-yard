package cc.lvhy.yard.dashboard.services;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * An Alias of a Service. {@code reason} is left out of the JSON while it is active.
 *
 * @param id its Instance Source ID: {@code <workstation>/<service>} of its Remote Service
 * @param workstation the Home Workstation of its Remote Service
 * @param service the name of its Remote Service
 * @param reason why it gives no Instances now
 */
public record Alias(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String id,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String workstation,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String service,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) State state,
    @JsonInclude(JsonInclude.Include.NON_NULL) String reason) {

  public enum State {

    /** It gives the Instances of its Remote Service. */
    ACTIVE,

    /** Yard Scope, but no Instance this Workstation can reach. */
    UNAVAILABLE,

    /** The Remote Service has no Yard Scope. */
    NOT_SHARED,

    UNREACHABLE,

    /** Its Workstation is not in the Yard, or has no Service of this name. */
    MISSING;

    @JsonValue
    public String value() {
      return name().toLowerCase().replace('_', '-');
    }
  }

  public boolean active() {
    return state == State.ACTIVE;
  }
}
