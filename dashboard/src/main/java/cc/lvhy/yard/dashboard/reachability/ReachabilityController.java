package cc.lvhy.yard.dashboard.reachability;

import cc.lvhy.yard.dashboard.Workstation;
import cc.lvhy.yard.dashboard.services.Service;
import cc.lvhy.yard.dashboard.services.YardReader;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** The REST API of {@link ReachabilityTester}. The package of the Services does not know it. */
@RestController
@RequestMapping(path = "/api", produces = MediaType.APPLICATION_JSON_VALUE)
class ReachabilityController {

  private final Workstation workstation;

  private final YardReader yard;

  private final ReachabilityTester reachability;

  ReachabilityController(
      Workstation workstation, YardReader yard, ReachabilityTester reachability) {
    this.workstation = workstation;
    this.yard = yard;
    this.reachability = reachability;
  }

  @GetMapping("/services/{workstation}/{service}/reachability")
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
    Service found =
        yard.service(workstation, service)
            .orElseThrow(() ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Workstation " + workstation + " has no Service " + service + "."));
    return reachability.test(found);
  }

  @GetMapping("/workstation/services/{service}/reachability")
  @Operation(
      operationId = "testOwnReachability",
      summary = "Tests whether this Workstation reaches one of its own Services",
      description =
          "As /api/services/{workstation}/{service}/reachability does, without naming this"
              + " Workstation.")
  @ApiResponse(
      responseCode = "200",
      description = "Tested, whether the checks passed or not",
      useReturnTypeSchema = true)
  @ApiResponse(responseCode = "404", description = "This Workstation has no Service of this name")
  Reachability ownReachability(@PathVariable String service) {
    Service found =
        yard.service(workstation.name(), service)
            .orElseThrow(() ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "This Workstation has no Service " + service + "."));
    return reachability.test(found);
  }
}
