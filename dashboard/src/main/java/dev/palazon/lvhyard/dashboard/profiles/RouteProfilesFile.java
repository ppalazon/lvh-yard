package dev.palazon.lvhyard.dashboard.profiles;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** What the dashboard read in {@code route-profiles.yaml} last. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RouteProfilesFile(
    @Schema(
            description =
                "Why the whole file cannot be read. The profiles are then the ones read before.")
        String error,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<RouteProfile> profiles) {

  public RouteProfilesFile {
    profiles = List.copyOf(profiles);
  }
}
