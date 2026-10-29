package dev.palazon.lvhyard.dashboard.services;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

/**
 * The Yard's Services and their Static Instances, as JSON. A Service is addressed by its Home Workstation and its
 * name; only this Workstation's Services can be changed.
 */
@RestController
@RequestMapping(path = "/api/services", produces = MediaType.APPLICATION_JSON_VALUE)
class ServicesController {

    private final YardReader yard;

    private final Registrations registrations;

    ServicesController(YardReader yard, Registrations registrations) {
        this.yard = yard;
        this.registrations = registrations;
    }

    @GetMapping
    @Operation(summary = "Every Service in the Yard, with its Instances and their Health")
    List<Service> services() {
        return yard.services();
    }

    @GetMapping("/{workstation}/{service}")
    @Operation(summary = "One Service, with its Instances and their Health")
    @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "404", description = "The Workstation has no Service of this name")
    Service service(@PathVariable String workstation, @PathVariable String service) {
        return yard.service(workstation, service).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Workstation " + workstation + " has no Service " + service + "."));
    }

    @PostMapping(path = "/{workstation}/{service}/instances", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registers a Static Instance; with no host, at this Workstation's LAN address",
            description = "The Instance gets a TCP check on its port, and is critical until the check first passes."
                    + " Registering the same host and port again replaces that Instance, which keeps its ID.")
    @ApiResponse(responseCode = "201", description = "Registered", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "400",
            description = "The name is not a single DNS label or is yard, the host is not an IP address or a"
                    + " hostname, or the port is missing or not a TCP port")
    @ApiResponse(responseCode = "403", description = "The Workstation is not this one")
    Instance register(@PathVariable String workstation, @PathVariable String service,
            @RequestBody StaticInstanceRequest request) {
        return registrations.register(workstation, service, request.host(), request.port());
    }

    @DeleteMapping("/{workstation}/{service}/instances/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Removes a Static Instance; a Service with nothing left leaves the Catalog")
    @ApiResponse(responseCode = "204", description = "Removed")
    @ApiResponse(responseCode = "403", description = "The Workstation is not this one")
    @ApiResponse(responseCode = "404", description = "The Service has no Static Instance with this ID")
    void remove(@PathVariable String workstation, @PathVariable String service, @PathVariable String id) {
        registrations.remove(workstation, service, id);
    }
}
