package cc.lvhy.yard.dashboard.backups;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Only this Workstation's setup is in a Backup, so the path does not name the Workstation. */
@RestController
@RequestMapping(path = "/api/workstation/backup")
class BackupController {

  static final String YAML = "application/yaml";

  private final BackupExport export;

  private final BackupImport backupImport;

  BackupController(BackupExport export, BackupImport backupImport) {
    this.export = export;
    this.backupImport = backupImport;
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

  @PostMapping(consumes = YAML, produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      operationId = "importBackup",
      summary = "Imports a Backup into this Workstation",
      description =
          "Mode add adds what is new and skips what exists. Mode update also overwrites what exists"
              + " with the values of the file. publication: none unpublishes a Service, and a"
              + " missing publication key keeps its Publication. The dashboard checks the whole"
              + " file first. If one entry is wrong, nothing changes. A Static Instance without a"
              + " host is at this Workstation's LAN address. A new Service without a publication"
              + " key stays Unpublished.")
  @ApiResponse(
      responseCode = "200",
      description = "The plan, and without a dry run what the import did",
      useReturnTypeSchema = true)
  @ApiResponse(
      responseCode = "400",
      description =
          "The file is refused, or the mode is unknown, and nothing changes. The problems give"
              + " each place in the file and its reason")
  @ApiResponse(
      responseCode = "500",
      description =
          "A write failed halfway. The detail says where it stopped. The applied entries stay,"
              + " and applied lists them. safetyBackup names the Backup to import to go back")
  ImportResult importBackup(
      @io.swagger.v3.oas.annotations.parameters.RequestBody(
              required = true,
              content = @Content(mediaType = YAML, schema = @Schema(type = "string")))
          @RequestBody
          String backup,
      @Parameter(
              description =
                  "add: adds what is new and skips what exists. update: also overwrites what"
                      + " exists.")
          @RequestParam
          String mode,
      @Parameter(description = "true shows the plan and changes nothing.")
          @RequestParam(name = "dry-run", defaultValue = "false")
          boolean dryRun) {
    return backupImport.run(backup, ImportMode.of(mode), dryRun);
  }
}
