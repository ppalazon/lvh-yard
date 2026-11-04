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
 * The paths do not name the Workstation, because only a Service's Home Workstation can change it.
 */
@RestController
@RequestMapping(path = "/api/workstation/services", produces = MediaType.APPLICATION_JSON_VALUE)
class WorkstationServicesController {

  private static final String INVALID_INSTANCE = "The host, port, check, tags or meta break a rule";

  private static final String CORE_INSTANCE_REFUSAL = "The Instance belongs to a Core Service";

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
  @Operation(operationId = "ownServices", summary = "Lists this Workstation's Services")
  List<Service> services() {
    return yard.services(workstation.name());
  }

  @GetMapping("/{service}")
  @Operation(operationId = "ownService", summary = "Gets one of this Workstation's Services")
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
      summary = "Tests whether this Workstation reaches one of its own Services",
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
      summary = "Registers a Static Instance",
      description =
          "With no host, the Instance is at this Workstation's LAN address. With no check, it gets"
              + " a TCP check on its port. It is critical until its check first passes. The same"
              + " host and port again replaces that Instance, which keeps its ID.")
  @ApiResponse(responseCode = "201", description = "Registered", useReturnTypeSchema = true)
  @ApiResponse(
      responseCode = "400",
      description =
          "The name is not a single DNS label or is taken by a Core Service. " + INVALID_INSTANCE)
  Instance register(@PathVariable String service, @RequestBody StaticInstanceRequest request) {
    return registrations.register(workstation.name(), service, request);
  }

  @PutMapping(path = "/{service}/instances/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      operationId = "editInstance",
      summary = "Edits a Static Instance",
      description =
          "A part left out gets its default, not its old value. The Instance keeps its ID. It is"
              + " critical until its check first passes.")
  @ApiResponse(responseCode = "200", description = "Edited", useReturnTypeSchema = true)
  @ApiResponse(
      responseCode = "400",
      description =
          INVALID_INSTANCE + ". Or another Instance of the Service is at the host and port")
  @ApiResponse(
      responseCode = "404",
      description = "The Service has no Static Instance with this ID")
  @ApiResponse(responseCode = "409", description = CORE_INSTANCE_REFUSAL)
  Instance edit(
      @PathVariable String service,
      @PathVariable String id,
      @RequestBody StaticInstanceRequest request) {
    return registrations.edit(workstation.name(), service, id, request);
  }

  @DeleteMapping("/{service}/instances/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Removes a Static Instance")
  @ApiResponse(responseCode = "204", description = "Removed")
  @ApiResponse(
      responseCode = "404",
      description = "The Service has no Static Instance with this ID")
  @ApiResponse(responseCode = "409", description = CORE_INSTANCE_REFUSAL)
  void remove(@PathVariable String service, @PathVariable String id) {
    registrations.remove(workstation.name(), service, id);
  }

  @PostMapping(path = "/{service}/container-bindings", consumes = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      operationId = "bindContainer",
      summary = "Binds a Service to a container or a Compose service",
      description =
          "Give either container, or composeProject and composeService. Each running container that"
              + " matches is a Bound Instance with a TCP check. Binding the same again changes"
              + " nothing.")
  @ApiResponse(responseCode = "201", description = "Bound", useReturnTypeSchema = true)
  @ApiResponse(
      responseCode = "400",
      description =
          "The name is not a single DNS label or is taken by a Core Service, the body names both or"
              + " neither of a container and a Compose service, a name is not one Docker or Compose"
              + " allows, or the port is not a TCP port")
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
      summary = "Removes a Container Binding and its Bound Instances",
      description = "A Service with nothing left leaves the Catalog.")
  @ApiResponse(responseCode = "204", description = "Removed")
  @ApiResponse(
      responseCode = "404",
      description = "The Service has no Container Binding with this ID")
  @ApiResponse(responseCode = "409", description = "The Service is a Core Service")
  void unbind(@PathVariable String service, @PathVariable String id) {
    containerBindings.remove(workstation.name(), service, id);
  }

  @PutMapping("/{service}/publication")
  @Operation(
      operationId = "publish",
      summary = "Publishes a Service, or changes its Scope, Instance Priority or Route Profiles",
      description =
          "With Yard Scope, the other Workstations also route it, at"
              + " <service>.<workstation>.<base-domain>. With no Scope, a new Publication gets"
              + " Local Scope and an existing one keeps its own. With no priority, a new"
              + " Publication gets static, bound, alias and an existing one keeps its own. An empty"
              + " priority turns the Instance Priority off: the passing Static and Bound Instances"
              + " share the Route. The Route applies the Route Profiles in order, as Traefik"
              + " middlewares named <workstation>-<profile>. With no profiles, a Publication keeps"
              + " its own. Empty profiles remove them all. A Core Service takes profiles too.")
  @ApiResponse(
      responseCode = "200",
      description = "The Service, published",
      useReturnTypeSchema = true)
  @ApiResponse(
      responseCode = "400",
      description =
          "The name is not a single DNS label or is taken by a Core Service, the Scope is not"
              + " local or yard, the priority repeats a kind or names an unknown one, or a Route"
              + " Profile is not among this Workstation's valid profiles")
  @ApiResponse(responseCode = "404", description = "This Workstation has no Service of this name")
  @ApiResponse(
      responseCode = "409",
      description = "The Service is a Core Service, and the Scope is yard or a priority is given")
  Service publish(
      @PathVariable String service, @RequestBody(required = false) PublicationRequest request) {
    publications.publish(
        workstation.name(),
        service,
        request == null ? null : request.scope(),
        request == null ? null : request.priority(),
        request == null ? null : request.profiles());
    return service(service);
  }

  @DeleteMapping("/{service}/publication")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      operationId = "unpublish",
      summary = "Unpublishes a Service",
      description = "A Service with no Instances left leaves the Catalog.")
  @ApiResponse(responseCode = "204", description = "Unpublished, or it was not published")
  @ApiResponse(responseCode = "404", description = "This Workstation has no Service of this name")
  @ApiResponse(responseCode = "409", description = "The Service is a Core Service")
  void unpublish(@PathVariable String service) {
    publications.unpublish(workstation.name(), service);
  }
}
