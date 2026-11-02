package dev.palazon.lvhyard.dashboard.services;

import dev.palazon.lvhyard.dashboard.Workstation;
import dev.palazon.lvhyard.dashboard.reachability.Reachability;
import dev.palazon.lvhyard.dashboard.reachability.ReachabilityTester;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * This Workstation's own Services, their Static Instances, Container Bindings and Publications, as
 * JSON. The paths do not name the Workstation: only a Service's Home Workstation can change it, so
 * there is no other one to name.
 */
@RestController
@RequestMapping(path = "/api/workstation/services", produces = MediaType.APPLICATION_JSON_VALUE)
class WorkstationServicesController {

  /** Why a Static Instance is refused, when it is registered or edited. */
  private static final String INVALID_INSTANCE =
      "The host is not an IP address or a hostname, the port is missing or not a TCP port, the"
          + " check is not http or tcp or has a bad path, interval or timeout, or a tag or a meta"
          + " key breaks its rules";

  private final Workstation workstation;

  private final YardReader yard;

  private final Registrations registrations;

  private final Publications publications;

  private final ContainerBindings containerBindings;

  private final ReachabilityTester reachability;

  WorkstationServicesController(
      Workstation workstation,
      YardReader yard,
      Registrations registrations,
      Publications publications,
      ContainerBindings containerBindings,
      ReachabilityTester reachability) {
    this.workstation = workstation;
    this.yard = yard;
    this.registrations = registrations;
    this.publications = publications;
    this.containerBindings = containerBindings;
    this.reachability = reachability;
  }

  @GetMapping
  @Operation(
      operationId = "ownServices",
      summary = "This Workstation's Services, with their Instances and their Health")
  List<Service> services() {
    return yard.services(workstation.name());
  }

  @GetMapping("/{service}")
  @Operation(
      operationId = "ownService",
      summary = "One of this Workstation's Services, with its Instances and their Health")
  @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
  @ApiResponse(responseCode = "404", description = "This Workstation has no Service of this name")
  Service service(@PathVariable String service) {
    return yard.service(workstation.name(), service)
        .orElseThrow(() ->
            new ResponseStatusException(
                HttpStatus.NOT_FOUND, "This Workstation has no Service " + service + "."));
  }

  @GetMapping("/{service}/reachability")
  @Operation(
      operationId = "testOwnReachability",
      summary = "Tests whether this Workstation reaches one of its own Services, now",
      description =
          "As /api/services/{workstation}/{service}/reachability does, without naming this"
              + " Workstation.")
  @ApiResponse(
      responseCode = "200",
      description = "Tested, whether the checks passed or not",
      useReturnTypeSchema = true)
  @ApiResponse(responseCode = "404", description = "This Workstation has no Service of this name")
  Reachability reachability(@PathVariable String service) {
    return reachability.test(service(service));
  }

  @PostMapping(path = "/{service}/instances", consumes = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Registers a Static Instance; with no host, at this Workstation's LAN address",
      description =
          "With no check, the Instance gets a TCP check on its port. It is critical until its check"
              + " first passes. Registering the same host and port again replaces that Instance,"
              + " which keeps its ID.")
  @ApiResponse(responseCode = "201", description = "Registered", useReturnTypeSchema = true)
  @ApiResponse(
      responseCode = "400",
      description = "The name is not a single DNS label or is yard. " + INVALID_INSTANCE)
  Instance register(@PathVariable String service, @RequestBody StaticInstanceRequest request) {
    return registrations.register(workstation.name(), service, request);
  }

  @PutMapping(path = "/{service}/instances/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      operationId = "editInstance",
      summary = "Edits a Static Instance, which stays the same Instance",
      description =
          "The Instance becomes what the body says, as when it is registered: a part left out gets"
              + " its default, not its old value. It is critical until its check first passes.")
  @ApiResponse(responseCode = "200", description = "Edited", useReturnTypeSchema = true)
  @ApiResponse(
      responseCode = "400",
      description =
          INVALID_INSTANCE + ". Or another Instance of the Service is at the host and port")
  @ApiResponse(
      responseCode = "404",
      description = "The Service has no Static Instance with this ID")
  Instance edit(
      @PathVariable String service,
      @PathVariable String id,
      @RequestBody StaticInstanceRequest request) {
    return registrations.edit(workstation.name(), service, id, request);
  }

  @DeleteMapping("/{service}/instances/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Removes a Static Instance; a Service with nothing left leaves the Catalog")
  @ApiResponse(responseCode = "204", description = "Removed")
  @ApiResponse(
      responseCode = "404",
      description = "The Service has no Static Instance with this ID")
  void remove(@PathVariable String service, @PathVariable String id) {
    registrations.remove(workstation.name(), service, id);
  }

  @PostMapping(path = "/{service}/container-bindings", consumes = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      operationId = "bindContainer",
      summary =
          "Binds a Service to a Docker container by its name, or to a"
              + " Compose project and service, and a container port",
      description =
          "Give either container, or composeProject and composeService. Each running container that"
              + " matches gives the Service a Bound Instance, so a scaled Compose service gives one"
              + " per container: at this Workstation's LAN address and the host port when the"
              + " container port is published, else at the container's own address and the"
              + " container port. It gets a TCP check on its port. Binding the same again changes"
              + " nothing.")
  @ApiResponse(responseCode = "201", description = "Bound", useReturnTypeSchema = true)
  @ApiResponse(
      responseCode = "400",
      description =
          "The name is not a single DNS label or is yard, neither or both of a container and a"
              + " Compose service are given, a name is not one Docker or Compose allows, or the"
              + " port is missing or not a TCP port")
  ContainerBinding bind(
      @PathVariable String service, @RequestBody ContainerBindingRequest request) {
    String id = containerBindings.add(workstation.name(), service, request);
    return service(service).containerBindings().stream()
        .filter(binding -> binding.id().equals(id))
        .findFirst()
        .orElseThrow();
  }

  @DeleteMapping("/{service}/container-bindings/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      operationId = "unbindContainer",
      summary = "Removes a Container Binding, and its Bound Instances",
      description = "A Service with nothing left leaves the Catalog.")
  @ApiResponse(responseCode = "204", description = "Removed")
  @ApiResponse(
      responseCode = "404",
      description = "The Service has no Container Binding with this ID")
  void unbind(@PathVariable String service, @PathVariable String id) {
    containerBindings.remove(workstation.name(), service, id);
  }

  @PutMapping("/{service}/publication")
  @Operation(
      operationId = "publish",
      summary = "Publishes a Service at <service>.<base-domain>, or changes its Scope",
      description =
          "The Route covers every Instance of the Service, including ones registered later. With"
              + " Yard Scope, every other Workstation also routes it, at"
              + " <service>.<workstation>.<base-domain>. With no Scope, a new Publication gets"
              + " Local Scope and one that exists keeps its own, so publishing again changes"
              + " nothing.")
  @ApiResponse(
      responseCode = "200",
      description = "The Service, published",
      useReturnTypeSchema = true)
  @ApiResponse(
      responseCode = "400",
      description =
          "The name is not a single DNS label or is yard, or the Scope is" + " not local or yard")
  @ApiResponse(responseCode = "404", description = "This Workstation has no Service of this name")
  Service publish(
      @PathVariable String service, @RequestBody(required = false) PublicationRequest request) {
    publications.publish(workstation.name(), service, request == null ? null : request.scope());
    return service(service);
  }

  @DeleteMapping("/{service}/publication")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      operationId = "unpublish",
      summary = "Unpublishes a Service, which keeps every Instance registered",
      description = "A Service with no Instances left leaves the Catalog.")
  @ApiResponse(responseCode = "204", description = "Unpublished, or it was not published")
  @ApiResponse(responseCode = "404", description = "This Workstation has no Service of this name")
  void unpublish(@PathVariable String service) {
    publications.unpublish(workstation.name(), service);
  }
}
