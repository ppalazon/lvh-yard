package cc.lvhy.yard.dashboard.services;

import cc.lvhy.yard.dashboard.Workstation;
import cc.lvhy.yard.dashboard.reachability.Reachability;
import cc.lvhy.yard.dashboard.reachability.ReachabilityTester;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

  private final Workstation workstation;

  private final YardReader yard;

  private final Registrations registrations;

  private final Publications publications;

  private final ContainerBindings containerBindings;

  private final Services services;

  private final Aliases aliases;

  private final ReachabilityTester reachability;

  WorkstationServicesController(
      Workstation workstation,
      YardReader yard,
      Registrations registrations,
      Publications publications,
      ContainerBindings containerBindings,
      Services services,
      Aliases aliases,
      ReachabilityTester reachability) {
    this.aliases = aliases;
    this.workstation = workstation;
    this.yard = yard;
    this.registrations = registrations;
    this.publications = publications;
    this.containerBindings = containerBindings;
    this.services = services;
    this.reachability = reachability;
  }

  @GetMapping
  @Operation(operationId = "ownServices", summary = "Lists this Workstation's Services")
  List<Service> services() {
    return yard.services(workstation.name());
  }

  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      operationId = "createService",
      summary = "Creates a Service, unless it exists",
      description =
          "The new Service is empty and Unpublished. It stays until you delete it. When this"
              + " Workstation already has a Service of this name, nothing changes and the answer"
              + " is that Service. That includes a Service that only Foreign Instances give.")
  @ApiResponse(
      responseCode = "200",
      description = "The Service existed",
      useReturnTypeSchema = true)
  @ApiResponse(responseCode = "201", description = "Created", useReturnTypeSchema = true)
  @ApiResponse(responseCode = "400", description = Registrations.INVALID_NAME)
  ResponseEntity<Service> create(@RequestBody NewServiceRequest request) {
    String name = request.name() == null ? "" : request.name().strip();
    boolean created = services.create(workstation.name(), name);
    return ResponseEntity.status(created ? HttpStatus.CREATED : HttpStatus.OK).body(service(name));
  }

  @DeleteMapping("/{service}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      operationId = "deleteService",
      summary = "Deletes a Service",
      description =
          "Removes its Publication, its Static Instances, and its Container Bindings with their"
              + " Bound Instances. Containers keep running. Foreign Instances stay, and the Service"
              + " stays while they do.")
  @ApiResponse(responseCode = "204", description = "Deleted")
  @ApiResponse(responseCode = "404", description = "This Workstation has no Service of this name")
  void delete(@PathVariable String service) {
    services.delete(workstation.name(), service);
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
      description = Registrations.INVALID_NAME + ". " + INVALID_INSTANCE)
  @ApiResponse(responseCode = "404", description = "This Workstation has no Service of this name")
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
              + " matches is a Bound Instance with a TCP check. Binding the same target again,"
              + " even with the same port, answers 409.")
  @ApiResponse(responseCode = "201", description = "Bound", useReturnTypeSchema = true)
  @ApiResponse(
      responseCode = "400",
      description =
          Registrations.INVALID_NAME
              + ", the body names both or neither of a container and a Compose service, a name is"
              + " not one Docker or Compose allows, or the port is not a TCP port")
  @ApiResponse(responseCode = "404", description = "This Workstation has no Service of this name")
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
      description = "The Service stays, even with nothing left.")
  @ApiResponse(responseCode = "204", description = "Removed")
  @ApiResponse(
      responseCode = "404",
      description = "The Service has no Container Binding with this ID")
  void unbind(@PathVariable String service, @PathVariable String id) {
    containerBindings.remove(workstation.name(), service, id);
  }

  @GetMapping("/{service}/aliases")
  @Operation(
      operationId = "aliases",
      summary = "Lists the Aliases of a Service",
      description = "In the order of its Instance Priority, each with its state.")
  @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
  @ApiResponse(responseCode = "404", description = "This Workstation has no Service of this name")
  List<Alias> aliases(@PathVariable String service) {
    return aliases.of(service);
  }

  @PostMapping(path = "/{service}/aliases", consumes = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      operationId = "addAlias",
      summary = "Adds an Alias to a Remote Service, unless the Service has it",
      description =
          "The Alias goes to the end of the order, and gives the Instances of the Remote Service."
              + " The Remote Service must have Yard Scope now. Later, the Alias stays when the"
              + " Remote Service loses Yard Scope or its Workstation stops answering, and it then"
              + " gives no Instances. Only this Workstation's Route uses its Aliases. With create,"
              + " a missing Service is created and published with Local Scope. When the Service has"
              + " the Alias already, nothing changes and the answer is that Alias.")
  @ApiResponse(
      responseCode = "200",
      description = "The Service had the Alias already. aliasCreated is false",
      useReturnTypeSchema = true)
  @ApiResponse(
      responseCode = "201",
      description =
          "Added, with its state. serviceCreated says whether the Service was missing and so"
              + " was created, or the Alias joined an existing Service",
      useReturnTypeSchema = true)
  @ApiResponse(
      responseCode = "400",
      description =
          Registrations.INVALID_NAME
              + ", the Workstation or Service name is not a DNS label, the Workstation is this"
              + " one, or the Remote Service is not there with Yard Scope. The answer names the"
              + " field")
  @ApiResponse(
      responseCode = "404",
      description = "This Workstation has no Service of this name, and create is not true")
  ResponseEntity<AddedAlias> addAlias(
      @PathVariable String service, @RequestBody AliasRequest request) {
    AddedAlias added =
        aliases.add(
            service,
            request.workstation(),
            request.service(),
            Boolean.TRUE.equals(request.create()));
    return ResponseEntity.status(added.aliasCreated() ? HttpStatus.CREATED : HttpStatus.OK)
        .body(added);
  }

  @DeleteMapping("/{service}/aliases/{workstation}/{remoteService}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      operationId = "removeAlias",
      summary = "Removes an Alias",
      description = "The Service stays, even with nothing left.")
  @ApiResponse(responseCode = "204", description = "Removed")
  @ApiResponse(
      responseCode = "404",
      description = "This Workstation has no such Service, or the Service has no such Alias")
  void removeAlias(
      @PathVariable String service,
      @PathVariable("workstation") String home,
      @PathVariable String remoteService) {
    aliases.remove(service, home, remoteService);
  }

  @PutMapping("/{service}/publication")
  @Operation(
      operationId = "publish",
      summary = "Publishes a Service, or changes its Scope, Route Profiles or access log",
      description =
          "With Yard Scope, the other Workstations also route it, at"
              + " <service>.<workstation>.<base-domain>. With no Scope, a new Publication gets"
              + " Local Scope and an existing one keeps its own. The Route applies the Route"
              + " Profiles in order. Middleware profiles are named <workstation>_<profile>. A"
              + " clientCertificate profile requires HTTPS and a Yard CA Client Certificate. With"
              + " no profiles, a Publication keeps its own. Empty profiles remove them all. With"
              + " accessLog true, Traefik logs the requests to the Route. With no accessLog, a new"
              + " Publication does not log them and an existing one keeps its own.")
  @ApiResponse(
      responseCode = "200",
      description = "The Service, published",
      useReturnTypeSchema = true)
  @ApiResponse(
      responseCode = "400",
      description =
          Registrations.INVALID_NAME
              + ", the Scope is not local or yard, or a Route Profile is not among this"
              + " Workstation's valid profiles")
  @ApiResponse(responseCode = "404", description = "This Workstation has no Service of this name")
  Service publish(
      @PathVariable String service, @RequestBody(required = false) PublicationRequest request) {
    publications.publish(
        workstation.name(), service, request == null ? PublicationRequest.KEEP_ALL : request);
    return service(service);
  }

  @GetMapping("/{service}/instance-priority")
  @Operation(
      operationId = "instancePriority",
      summary = "Gets the Instance Priority of a Service",
      description =
          "Its Instance Sources in order, whether the order is on, and the source that the Route"
              + " uses now.")
  @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
  @ApiResponse(responseCode = "404", description = "This Workstation has no Service of this name")
  InstancePriority instancePriority(@PathVariable String service) {
    return InstancePriority.of(service(service));
  }

  @PutMapping(path = "/{service}/instance-priority", consumes = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      operationId = "setInstancePriority",
      summary = "Sets the order of the Instance Sources of a Service, or turns the order on or off",
      description =
          "The IDs in order go first, and the other sources follow in their order. With on true,"
              + " the Route uses the passing Instances of the first source that has one. With on"
              + " false, every passing Instance of every source shares the Route, and the order"
              + " stays. Unpublishing keeps both.")
  @ApiResponse(responseCode = "200", description = "Set", useReturnTypeSchema = true)
  @ApiResponse(
      responseCode = "400",
      description = "The order names an ID twice, or an ID that is not one of its Instance Sources")
  @ApiResponse(responseCode = "404", description = "This Workstation has no Service of this name")
  InstancePriority setInstancePriority(
      @PathVariable String service, @RequestBody InstancePriorityRequest request) {
    services.setInstancePriority(workstation.name(), service, request);
    return InstancePriority.of(service(service));
  }

  @DeleteMapping("/{service}/publication")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      operationId = "unpublish",
      summary = "Unpublishes a Service",
      description = "The Service stays, Unpublished.")
  @ApiResponse(responseCode = "204", description = "Unpublished, or it was not published")
  @ApiResponse(responseCode = "404", description = "This Workstation has no Service of this name")
  void unpublish(@PathVariable String service) {
    publications.unpublish(workstation.name(), service);
  }
}
