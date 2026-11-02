package dev.palazon.lvhyard.dashboard.services;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** The Yard, with each Workstation's Services, as JSON, read only. */
@RestController
class YardController {

  private final YardReader yard;

  YardController(YardReader yard) {
    this.yard = yard;
  }

  @GetMapping(path = "/api/yard", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      operationId = "yard",
      summary = "This Workstation and every other one in the Yard, with their Services",
      description =
          "The other Workstations come by name. One whose Consul datacenter does not answer is"
              + " unreachable, with no Services, and the others still load.")
  Yard yard() {
    return yard.yard();
  }
}
