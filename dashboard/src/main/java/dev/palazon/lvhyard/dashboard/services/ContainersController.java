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

  private final ContainerRegistration containerRegistration;

  ContainersController(ContainerRegistration containerRegistration) {
    this.containerRegistration = containerRegistration;
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
    return containerRegistration.unfollowed();
  }

  @PostMapping("/{container}/register")
  @Operation(
      operationId = "registerContainer",
      summary = "Gives a container a Service",
      description =
          "Binds only the named container, even when it has Compose labels. The Service is"
              + " named after the container, cleaned to follow the Service name rule, unless"
              + " service is given. The port is needed when the container does not run, or does"
              + " not have exactly one TCP port. The Service must exist, and it keeps its"
              + " Publication. The body can be left out.")
  @ApiResponse(
      responseCode = "200",
      description = "The Service, with the container bound",
      useReturnTypeSchema = true)
  @ApiResponse(
      responseCode = "400",
      description = Registrations.INVALID_NAME + ", or the port is missing or not a TCP port")
  @ApiResponse(
      responseCode = "404",
      description = "This Workstation has no Service of this name. The problem names it as service")
  @ApiResponse(
      responseCode = "409",
      description = "A Container Binding already follows the container")
  @ApiResponse(responseCode = "503", description = UNREADABLE_DOCKER)
  Service registerContainer(
      @PathVariable String container,
      @RequestBody(required = false) ContainerRegistrationRequest request) {
    return containerRegistration.registerByName(
        container, request == null ? ContainerRegistrationRequest.DEFAULTS : request);
  }
}
