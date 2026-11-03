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
      summary = "How LVH Yard on this Workstation is: each part, and the worst of them",
      description =
          "Each part is ok, warning or failing, with a reason and what was found. The parts are"
              + " read side by side with a short timeout, and the answer is kept for about 5 s.")
  Status status() {
    return status.status();
  }
}
