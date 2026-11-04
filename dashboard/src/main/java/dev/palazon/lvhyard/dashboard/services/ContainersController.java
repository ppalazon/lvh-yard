package dev.palazon.lvhyard.dashboard.services;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/workstation/containers", produces = MediaType.APPLICATION_JSON_VALUE)
class ContainersController {

  private static final String UNREADABLE_DOCKER =
      "Docker does not answer, so its containers are not known";

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
  @ApiResponse(responseCode = "503", description = UNREADABLE_DOCKER)
  List<UnfollowedContainer> containers() {
    return containerImport.unfollowed();
  }

  @PostMapping("/{container}/import")
  @Operation(
      operationId = "importContainer",
      summary = "Gives a running container a Service",
      description =
          "Binds the container to a Service: by Compose project and service when it has them, else"
              + " by container name. The Service is named after the Compose service or the"
              + " container, cleaned to follow the Service name rule, unless service is given. The"
              + " port is needed when the container does"
              + " not have exactly one TCP port. A new Service is published with Local Scope,"
              + " unless publish is false. A Service that exists keeps its Publication, or stays"
              + " unpublished. The body can be left out.")
  @ApiResponse(
      responseCode = "200",
      description = "The Service, with the container bound",
      useReturnTypeSchema = true)
  @ApiResponse(
      responseCode = "400",
      description =
          "The Service name is not a single DNS label or is consul, or the port is missing or not"
              + " a TCP port")
  @ApiResponse(responseCode = "404", description = "No running container has this name")
  @ApiResponse(
      responseCode = "409",
      description = "A Container Binding already follows the container")
  @ApiResponse(responseCode = "503", description = UNREADABLE_DOCKER)
  Service importContainer(
      @PathVariable String container,
      @RequestBody(required = false) ContainerImportRequest request) {
    return containerImport.importContainer(
        container, request == null ? ContainerImportRequest.DEFAULTS : request);
  }
}
