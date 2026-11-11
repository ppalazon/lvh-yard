package cc.lvhy.yard.dashboard.status;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Objects;

/** {@code reason} is a short sentence, ready to show. */
@Schema(name = "StatusPart")
public record Part(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) PartState state,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String reason,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Detail> details) {

  public Part {
    details = List.copyOf(details);
  }

  /** As bad as the worst of its {@code details}, or ok when none has a state. */
  public static Part of(String name, String reason, List<Detail> details) {
    return new Part(
        name,
        PartState.worst(details.stream().map(Detail::state).filter(Objects::nonNull).toList()),
        reason,
        details);
  }

  public static Part failing(String name, String reason) {
    return new Part(name, PartState.FAILING, reason, List.of());
  }

  /** {@code state} is null for a plain fact, such as a version. */
  @Schema(name = "StatusDetail")
  public record Detail(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String value,
      @JsonInclude(JsonInclude.Include.NON_NULL) PartState state) {

    public static Detail fact(String name, String value) {
      return new Detail(name, value, null);
    }
  }
}
