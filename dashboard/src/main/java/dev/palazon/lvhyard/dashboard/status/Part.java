package dev.palazon.lvhyard.dashboard.status;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Objects;

/**
 * One part of LVH Yard on this Workstation, such as Consul or Docker, and how it is.
 *
 * @param name the part's name, such as {@code Consul}
 * @param state ok, warning or failing
 * @param reason a short sentence that says why, ready to show
 * @param details what was found, one line each
 */
@Schema(name = "StatusPart")
public record Part(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) PartState state,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String reason,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Detail> details) {

  public Part {
    details = List.copyOf(details);
  }

  /** A part that is as bad as the worst of its {@code details}, or ok when none has a state. */
  public static Part of(String name, String reason, List<Detail> details) {
    return new Part(
        name,
        PartState.worst(details.stream().map(Detail::state).filter(Objects::nonNull).toList()),
        reason,
        details);
  }

  /** A part that failed for {@code reason}, with nothing else found. */
  public static Part failing(String name, String reason) {
    return new Part(name, PartState.FAILING, reason, List.of());
  }

  /**
   * One thing found about a part, such as its version, or one Workstation in Consul's WAN pool.
   *
   * @param name what it is about, such as {@code version} or {@code ws-b}
   * @param value what was found, such as {@code 28.1.1} or {@code alive at 10.0.0.2:8302}
   * @param state ok, warning or failing when this thing has a state of its own; none for a plain
   *     fact such as a version
   */
  @Schema(name = "StatusDetail")
  public record Detail(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String value,
      @JsonInclude(JsonInclude.Include.NON_NULL) PartState state) {

    /** A plain fact, with no state of its own. */
    public static Detail fact(String name, String value) {
      return new Detail(name, value, null);
    }
  }
}
