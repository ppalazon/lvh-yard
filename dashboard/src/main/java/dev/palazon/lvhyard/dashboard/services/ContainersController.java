package dev.palazon.lvhyard.dashboard.services;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/workstation/containers", produces = MediaType.APPLICATION_JSON_VALUE)
class ContainersController {

  private final ContainerList containerList;

  ContainersController(ContainerList containerList) {
    this.containerList = containerList;
  }

  @GetMapping
  @Operation(
      operationId = "containers",
      summary = "Lists every container of this Workstation, stopped ones too",
      description =
          "Compose projects come first, then the other containers. Each port is a TCP container"
              + " port, with its host port when it is published. A stopped container has no"
              + " ports. followedBy names the Services that have a Container Binding that matches"
              + " the container, whatever its port, and is empty when none does.")
  @ApiResponse(responseCode = "200", description = "The containers", useReturnTypeSchema = true)
  @ApiResponse(
      responseCode = "503",
      description = "Docker does not answer, so its containers are not known")
  List<ListedContainer> containers() {
    return containerList.containers();
  }
}
