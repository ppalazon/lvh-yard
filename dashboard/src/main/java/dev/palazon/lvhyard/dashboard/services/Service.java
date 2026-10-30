package dev.palazon.lvhyard.dashboard.services;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A Service as the Yard sees it.
 *
 * @param workstation its Home Workstation
 * @param name its name, a single DNS label
 * @param state what its Publication and the Health of its Instances add up to
 * @param publication its Publication; {@code null}, and left out of the JSON, when it is unpublished
 * @param hostname where this Workstation routes it while it has Instances; {@code null}, and left out of the
 *        JSON, when it is unpublished
 * @param instances its registered Instances, ordered by host and port
 */
public record Service(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String workstation,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ServiceState state,
        @JsonInclude(JsonInclude.Include.NON_NULL) Publication publication,
        @JsonInclude(JsonInclude.Include.NON_NULL) String hostname,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Instance> instances) {

    /** A Service in the state its {@code publication} and {@code instances} add up to. */
    Service(String workstation, String name, Publication publication, String hostname, List<Instance> instances) {
        this(workstation, name, ServiceState.of(publication, instances), publication, hostname, instances);
    }

    /** Whether it has a Publication, whatever the Health of its Instances. */
    public boolean published() {
        return publication != null;
    }
}
