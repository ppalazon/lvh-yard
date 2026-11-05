package dev.palazon.lvhyard.dashboard.backups;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Only this Workstation's setup is in a Backup, so the path does not name the Workstation. */
@RestController
@RequestMapping(path = "/api/workstation/backup")
class BackupController {

  static final String YAML = "application/yaml";

  private final BackupExport export;

  BackupController(BackupExport export) {
    this.export = export;
  }

  @GetMapping(produces = YAML)
  @Operation(
      operationId = "backup",
      summary = "Exports a Backup of this Workstation's setup",
      description =
          "The Backup holds this Workstation's Services with their Publications, Static"
              + " Instances and Container Bindings, its Aliases and its Route Profiles. It holds"
              + " basic-auth hashes, so keep it private.")
  @ApiResponse(responseCode = "200", description = "The Backup, as YAML")
  String backup() {
    return BackupFormat.write(export.backup());
  }
}
