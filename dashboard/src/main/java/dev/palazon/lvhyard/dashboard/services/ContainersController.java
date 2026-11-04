package dev.palazon.lvhyard.dashboard.services;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The running containers on this Workstation's own Docker. */
@RestController
@RequestMapping(path = "/api/workstation/containers", produces = MediaType.APPLICATION_JSON_VALUE)
class ContainersController {

  private final ContainerImport containerImport;

  ContainersController(ContainerImport containerImport) {
    this.containerImport = containerImport;
  }

  @GetMapping
  @Operation(
      operationId = "containers",
      summary = "Lists the running containers that no Container Binding follows",
      description =
          "Compose projects come first, then the other containers. Each port is a TCP container"
              + " port, with its host port when it is published.")
  @ApiResponse(responseCode = "200", description = "The containers", useReturnTypeSchema = true)
  @ApiResponse(
      responseCode = "503",
      description = "Docker does not answer, so its containers are not known")
  List<UnfollowedContainer> containers() {
    return containerImport.unfollowed();
  }
}
