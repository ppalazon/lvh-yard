package dev.palazon.lvhyard.dashboard.services;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

/**
 * The Yard's Services, as JSON, read only. A Service is addressed by its Home Workstation and its name; this
 * Workstation's own Services are changed through {@link WorkstationServicesController}.
 */
@RestController
@RequestMapping(path = "/api/services", produces = MediaType.APPLICATION_JSON_VALUE)
class ServicesController {

    private final YardReader yard;

    ServicesController(YardReader yard) {
        this.yard = yard;
    }

    @GetMapping
    @Operation(operationId = "yardServices", summary = "Every Service in the Yard, with its Instances and their Health")
    List<Service> services(@RequestParam(required = false)
            @Parameter(description = "Only the Services of this Workstation") String workstation) {
        return workstation == null ? yard.services() : yard.services(workstation);
    }

    @GetMapping("/{workstation}/{service}")
    @Operation(operationId = "yardService", summary = "One Service, with its Instances and their Health")
    @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "404", description = "The Workstation has no Service of this name")
    Service service(@PathVariable String workstation, @PathVariable String service) {
        return yard.service(workstation, service).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Workstation " + workstation + " has no Service " + service + "."));
    }
}
