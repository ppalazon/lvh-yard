package dev.palazon.lvhyard.dashboard.services;

import dev.palazon.lvhyard.dashboard.reachability.Reachability;
import dev.palazon.lvhyard.dashboard.reachability.ReachabilityTester;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.util.List;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Read only. This Workstation's own Services are changed through {@link
 * WorkstationServicesController}, and a Remote Service only on its Home Workstation.
 */
@RestController
@RequestMapping(path = "/api/services", produces = MediaType.APPLICATION_JSON_VALUE)
class ServicesController {

  private final YardReader yard;

  private final ReachabilityTester reachability;

  ServicesController(YardReader yard, ReachabilityTester reachability) {
    this.yard = yard;
    this.reachability = reachability;
  }

  @GetMapping
  @Operation(
      operationId = "yardServices",
      summary = "Lists every Service in the Yard",
      description =
          "This Workstation's Services first, then by Workstation and name. An unreachable"
              + " Workstation's Services are left out: /api/yard shows which ones.")
  @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
  @ApiResponse(responseCode = "400", description = "No Service state has this name")
  @ApiResponse(responseCode = "404", description = "The Yard has no Workstation of this name")
  @ApiResponse(responseCode = "503", description = "The Workstation is unreachable")
  List<Service> services(
      @RequestParam(required = false)
          @Parameter(description = "Only the Services of this Workstation")
          String workstation,
      @RequestParam(required = false)
          @Parameter(
              description = "Only the Services in this state",
              schema = @Schema(implementation = ServiceState.class))
          String state) {
    ServiceState only = state == null ? null : ServiceState.parse(state);
    List<Service> services = workstation == null ? yard.services() : yard.services(workstation);
    return only == null
        ? services
        : services.stream().filter(service -> service.state() == only).toList();
  }

  @GetMapping("/{workstation}/{service}")
  @Operation(operationId = "yardService", summary = "Gets one Service")
  @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
  @ApiResponse(
      responseCode = "404",
      description =
          "The Yard has no Workstation of this name, or it has no" + " Service of this name")
  @ApiResponse(responseCode = "503", description = "The Workstation is unreachable")
  Service service(@PathVariable String workstation, @PathVariable String service) {
    return yard.service(workstation, service)
        .orElseThrow(() ->
            new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Workstation " + workstation + " has no Service " + service + "."));
  }

  @GetMapping("/{workstation}/{service}/reachability")
  @Operation(
      operationId = "testReachability",
      summary = "Tests whether this Workstation reaches a Service",
      description =
          "Connects to each Instance, and requests each hostname through this Workstation's"
              + " Traefik. The results are not stored.")
  @ApiResponse(
      responseCode = "200",
      description = "Tested, whether the checks passed or not",
      useReturnTypeSchema = true)
  @ApiResponse(
      responseCode = "404",
      description =
          "The Yard has no Workstation of this name, or it has no" + " Service of this name")
  @ApiResponse(responseCode = "503", description = "The Workstation is unreachable")
  Reachability reachability(@PathVariable String workstation, @PathVariable String service) {
    return reachability.test(service(workstation, service));
  }

  /**
   * Refuses any change, with the reason. Left out of the OpenAPI document, which lists only what
   * can be done.
   */
  @Hidden
  @RequestMapping(
      path = "/**",
      method = {RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE})
  ResponseEntity<ProblemDetail> change() {
    return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
        .allow(HttpMethod.GET)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(
            ProblemDetail.forStatusAndDetail(
                HttpStatus.METHOD_NOT_ALLOWED,
                "The Services of the Yard are read only here: a Service can be changed only on its"
                    + " Home Workstation. Change this Workstation's own Services through"
                    + " /api/workstation/services."));
  }
}
