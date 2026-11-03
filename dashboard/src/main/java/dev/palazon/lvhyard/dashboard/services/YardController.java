package dev.palazon.lvhyard.dashboard.services;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class YardController {

  private final YardReader yard;

  YardController(YardReader yard) {
    this.yard = yard;
  }

  @GetMapping(path = "/api/yard", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      operationId = "yard",
      summary = "Gets every Workstation of the Yard, with its Services",
      description =
          "The other Workstations come by name. An unreachable one has no Services, and the others"
              + " still load.")
  Yard yard() {
    return yard.yard();
  }
}
