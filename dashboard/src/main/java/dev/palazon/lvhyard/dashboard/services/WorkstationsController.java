package dev.palazon.lvhyard.dashboard.services;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;

/** The Yard's Workstations, each with its Services, as JSON, read only. */
@RestController
class WorkstationsController {

    private final YardReader yard;

    WorkstationsController(YardReader yard) {
        this.yard = yard;
    }

    @GetMapping(path = "/api/workstations", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "workstations", summary = "Every Workstation in the Yard with its Services, this one first",
            description = "The other Workstations follow by name. A Workstation whose Consul datacenter does not"
                    + " answer is unreachable, with no Services, and the others still load.")
    List<HomeWorkstation> workstations() {
        return yard.workstations();
    }
}
