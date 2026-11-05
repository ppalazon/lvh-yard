package dev.palazon.lvhyard.dashboard.backups;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * The plan of an import. Without a dry run, the import has done it.
 *
 * @param exportedFrom where the Backup comes from, as the file says; {@code null} when it leaves it
 *     out
 * @param entries in the order of the file: Route Profiles, then each Service with its entries, then
 *     Aliases
 * @param safetyBackup the file in the Backups folder with the setup from before the import; {@code
 *     null} when the import changed nothing
 */
public record ImportResult(
    @JsonInclude(JsonInclude.Include.NON_NULL) ExportedFrom exportedFrom,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ImportMode mode,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean dryRun,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<PlanEntry> entries,
    @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(
            description =
                "The safety Backup in the Backups folder, with the setup from before the import."
                    + " Only an import that changed something writes one.")
        String safetyBackup) {

  /**
   * @param at as ISO 8601, such as {@code 2026-11-05T10:00:00+01:00}
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record ExportedFrom(String workstation, String at) {}
}
