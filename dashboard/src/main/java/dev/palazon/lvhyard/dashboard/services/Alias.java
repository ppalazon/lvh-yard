package dev.palazon.lvhyard.dashboard.services;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A short hostname this Workstation gives to a Remote Service, for its own use only.
 *
 * @param name its name, a single DNS label, as a Service name is
 * @param workstation the Remote Service's Home Workstation
 * @param service the Remote Service's name
 * @param hostname where this Workstation routes it while it is active, {@code
 *     <alias>.<base-domain>}
 * @param state whether it has a Route here, or why not
 * @param reason why it has no Route, in words; {@code null}, and left out of the JSON, while it is
 *     active
 */
public record Alias(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String workstation,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String service,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String hostname,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) State state,
    @JsonInclude(JsonInclude.Include.NON_NULL) String reason) {

  /** Whether an Alias has a Route here, or why not. */
  public enum State {

    /** It names a Remote Service with Yard Scope, and is routed to its Instances. */
    ACTIVE,

    /**
     * The Remote Service has Yard Scope, but no Instance this Workstation can reach, so it has no
     * Route.
     */
    UNAVAILABLE,

    /** A published Service of this Workstation has the same name, and its Route wins. */
    HIDDEN,

    /**
     * The Remote Service is not published with Yard Scope, so this Workstation has no Route for it.
     */
    NOT_SHARED,

    /** Its Workstation is unreachable, so its Services are not known for now. */
    UNREACHABLE,

    /** Its Workstation is not in the Yard, or has no Service of this name. */
    MISSING;

    /** The state's name in the API, such as {@code not-shared}. */
    @JsonValue
    public String value() {
      return name().toLowerCase().replace('_', '-');
    }
  }

  /** Whether it is routed here. */
  public boolean active() {
    return state == State.ACTIVE;
  }
}
