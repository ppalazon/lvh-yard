package dev.palazon.lvhyard.dashboard.services;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * This Workstation's Aliases, as JSON. They exist only here, so the paths do not name the
 * Workstation.
 */
@RestController
@RequestMapping(path = "/api/workstation/aliases", produces = MediaType.APPLICATION_JSON_VALUE)
class AliasesController {

  private final Aliases aliases;

  AliasesController(Aliases aliases) {
    this.aliases = aliases;
  }

  @GetMapping
  @Operation(
      operationId = "aliases",
      summary = "This Workstation's Aliases, by name, each with its state")
  List<Alias> aliases() {
    return aliases.all();
  }

  @PutMapping(path = "/{alias}", consumes = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      operationId = "addAlias",
      summary =
          "Gives a Remote Service the Alias <alias>.<base-domain>, on this" + " Workstation only")
  @ApiResponse(
      responseCode = "200",
      description = "The Alias, with its state",
      useReturnTypeSchema = true)
  @ApiResponse(
      responseCode = "400",
      description =
          "The name is not a single DNS label or is yard, or the" + " Workstation is this one")
  @ApiResponse(
      responseCode = "404",
      description =
          "The Yard has no Workstation of this name, or it has no" + " Service of this name")
  @ApiResponse(
      responseCode = "503",
      description = "The Workstation is unreachable, so its Services are not known")
  Alias add(@PathVariable String alias, @RequestBody AliasRequest request) {
    return aliases.add(alias, request.workstation(), request.service());
  }

  @DeleteMapping("/{alias}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(operationId = "removeAlias", summary = "Removes an Alias, and its Route")
  @ApiResponse(responseCode = "204", description = "Removed")
  @ApiResponse(responseCode = "404", description = "This Workstation has no Alias of this name")
  void remove(@PathVariable String alias) {
    aliases.remove(alias);
  }
}
