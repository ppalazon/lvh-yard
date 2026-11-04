package dev.palazon.lvhyard.dashboard.status;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class StatusController {

  private final StatusReader status;

  StatusController(StatusReader status) {
    this.status = status;
  }

  @GetMapping(path = "/api/status", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      operationId = "status",
      summary = "Gets whether LVH Yard works on this Workstation",
      description = "Each part is ok, warning or failing. The answer is kept for about 5 s.")
  Status status() {
    return status.status();
  }
}
