package cc.lvhy.yard.dashboard.status;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * @param parts in the order the status page shows them
 */
public record Status(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) PartState overall,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Part> parts) {

  public static Status of(List<Part> parts) {
    return new Status(
        PartState.worst(parts.stream().map(Part::state).toList()), List.copyOf(parts));
  }
}
