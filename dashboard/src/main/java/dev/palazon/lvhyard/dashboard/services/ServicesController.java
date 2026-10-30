package dev.palazon.lvhyard.dashboard.services;

import java.util.List;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

/**
 * The Yard's Services, as JSON, read only. A Service is addressed by its Home Workstation and its name; this
 * Workstation's own Services are changed through {@link WorkstationServicesController}, and a Remote Service only on
 * its Home Workstation.
 */
@RestController
@RequestMapping(path = "/api/services", produces = MediaType.APPLICATION_JSON_VALUE)
class ServicesController {

    private final YardReader yard;

    ServicesController(YardReader yard) {
        this.yard = yard;
    }

    @GetMapping
    @Operation(operationId = "yardServices", summary = "Every Service in the Yard, with its Instances and their Health",
            description = "This Workstation's Services first, then the others' by Workstation and name. The Services"
                    + " of an unreachable Workstation are left out; /api/yard shows which ones are.")
    @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "404", description = "The Yard has no Workstation of this name")
    @ApiResponse(responseCode = "503", description = "The Workstation asked for is unreachable: its Consul datacenter"
            + " does not answer")
    List<Service> services(@RequestParam(required = false)
            @Parameter(description = "Only the Services of this Workstation") String workstation) {
        return workstation == null ? yard.services() : yard.services(workstation);
    }

    @GetMapping("/{workstation}/{service}")
    @Operation(operationId = "yardService", summary = "One Service, with its Instances and their Health")
    @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "404", description = "The Yard has no Workstation of this name, or it has no"
            + " Service of this name")
    @ApiResponse(responseCode = "503", description = "The Workstation is unreachable: its Consul datacenter does not"
            + " answer")
    Service service(@PathVariable String workstation, @PathVariable String service) {
        return yard.service(workstation, service).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Workstation " + workstation + " has no Service " + service + "."));
    }

    /**
     * Any change asked of a Service here, such as registering an Instance of a Remote Service, is refused with the
     * reason: no path here changes a Service. Left out of the OpenAPI document, which lists only what can be done.
     */
    @Hidden
    @RequestMapping(path = "/**", method = { RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH,
            RequestMethod.DELETE })
    ResponseEntity<ProblemDetail> change() {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).allow(HttpMethod.GET)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.METHOD_NOT_ALLOWED, "The Services of the Yard are"
                        + " read only here: a Service can be changed only on its Home Workstation. Change this"
                        + " Workstation's own Services through /api/workstation/services."));
    }
}
