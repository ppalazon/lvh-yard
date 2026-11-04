package dev.palazon.lvhyard.dashboard.profiles;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The Route Profiles exist only here, so the path does not name the Workstation. */
@RestController
@RequestMapping(
    path = "/api/workstation/route-profiles",
    produces = MediaType.APPLICATION_JSON_VALUE)
class RouteProfilesController {

  private final RouteProfiles profiles;

  RouteProfilesController(RouteProfiles profiles) {
    this.profiles = profiles;
  }

  @GetMapping
  @Operation(
      operationId = "routeProfiles",
      summary = "Lists this Workstation's Route Profiles, with their types and errors")
  RouteProfilesFile routeProfiles() {
    return profiles.file();
  }
}
