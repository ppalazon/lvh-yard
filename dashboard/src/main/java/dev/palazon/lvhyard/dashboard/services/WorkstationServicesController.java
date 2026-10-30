package dev.palazon.lvhyard.dashboard.services;

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

import dev.palazon.lvhyard.dashboard.Workstation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

/**
 * This Workstation's own Services, their Static Instances and their Publications, as JSON. The paths do not name
 * the Workstation: only a Service's Home Workstation can change it, so there is no other one to name.
 */
@RestController
@RequestMapping(path = "/api/workstation/services", produces = MediaType.APPLICATION_JSON_VALUE)
class WorkstationServicesController {

    private final Workstation workstation;

    private final YardReader yard;

    private final Registrations registrations;

    private final Publications publications;

    WorkstationServicesController(Workstation workstation, YardReader yard, Registrations registrations,
            Publications publications) {
        this.workstation = workstation;
        this.yard = yard;
        this.registrations = registrations;
        this.publications = publications;
    }

    @GetMapping
    @Operation(operationId = "ownServices", summary = "This Workstation's Services, with their Instances and their Health")
    List<Service> services() {
        return yard.services(workstation.name());
    }

    @GetMapping("/{service}")
    @Operation(operationId = "ownService", summary = "One of this Workstation's Services, with its Instances and their Health")
    @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "404", description = "This Workstation has no Service of this name")
    Service service(@PathVariable String service) {
        return yard.service(workstation.name(), service).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "This Workstation has no Service " + service + "."));
    }

    @PostMapping(path = "/{service}/instances", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registers a Static Instance; with no host, at this Workstation's LAN address",
            description = "The Instance gets a TCP check on its port, and is critical until the check first passes."
                    + " Registering the same host and port again replaces that Instance, which keeps its ID.")
    @ApiResponse(responseCode = "201", description = "Registered", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "400",
            description = "The name is not a single DNS label or is yard, the host is not an IP address or a"
                    + " hostname, or the port is missing or not a TCP port")
    Instance register(@PathVariable String service, @RequestBody StaticInstanceRequest request) {
        return registrations.register(workstation.name(), service, request.host(), request.port());
    }

    @DeleteMapping("/{service}/instances/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Removes a Static Instance; a Service with nothing left leaves the Catalog")
    @ApiResponse(responseCode = "204", description = "Removed")
    @ApiResponse(responseCode = "404", description = "The Service has no Static Instance with this ID")
    void remove(@PathVariable String service, @PathVariable String id) {
        registrations.remove(workstation.name(), service, id);
    }

    @PutMapping("/{service}/publication")
    @Operation(operationId = "publish", summary = "Publishes a Service at <service>.<base-domain>, with Local Scope",
            description = "The Route covers every Instance of the Service, including ones registered later."
                    + " Publishing a published Service again changes nothing.")
    @ApiResponse(responseCode = "200", description = "The Service, published", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "400", description = "The name is not a single DNS label or is yard")
    @ApiResponse(responseCode = "404", description = "This Workstation has no Service of this name")
    Service publish(@PathVariable String service) {
        publications.publish(workstation.name(), service);
        return service(service);
    }

    @DeleteMapping("/{service}/publication")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "unpublish", summary = "Unpublishes a Service, which keeps every Instance registered",
            description = "A Service with no Instances left leaves the Catalog.")
    @ApiResponse(responseCode = "204", description = "Unpublished, or it was not published")
    @ApiResponse(responseCode = "404", description = "This Workstation has no Service of this name")
    void unpublish(@PathVariable String service) {
        publications.unpublish(workstation.name(), service);
    }
}
