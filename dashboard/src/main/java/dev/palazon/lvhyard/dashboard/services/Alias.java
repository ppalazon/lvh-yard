package dev.palazon.lvhyard.dashboard.services;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * For this Workstation's own use only. {@code reason} is left out of the JSON while it is active.
 */
public record Alias(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String workstation,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String service,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String hostname,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) State state,
    @JsonInclude(JsonInclude.Include.NON_NULL) String reason) {

  public enum State {

    /** Routed to the Remote Service's Instances. */
    ACTIVE,

    /** Yard Scope, but no Instance this Workstation can reach. */
    UNAVAILABLE,

    /**
     * A published Service of this Workstation has the same name. Its Route uses the Remote Service
     * only when none of its own Instances passes.
     */
    FALLBACK,

    /**
     * A published Service of this Workstation has the same name, and its Instance Priority leaves
     * out the Alias.
     */
    HIDDEN,

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
