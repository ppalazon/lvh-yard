package dev.palazon.lvhyard.dashboard.services;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A link from a Service to a Docker container by its name, or to a Compose project and service, and a container
 * port. Every running container that matches gives the Service a Bound Instance at its current address and port.
 *
 * @param id the Binding's ID, {@code <container>:<port>} or {@code compose:<project>:<service>:<port>}, which the
 *        Service's Container Bindings path names it by
 * @param container the name of the container it follows; {@code null}, and left out of the JSON, for a Compose
 *        service
 * @param composeProject the Compose project whose service it follows; {@code null}, and left out of the JSON, for a
 *        container by name
 * @param composeService the Compose service it follows; {@code null}, and left out of the JSON, for a container by
 *        name
 * @param port the container port the Service listens on, inside the container
 * @param instances the IDs of the Bound Instances it gives the Service now, one per matching container; none while
 *        no container matches
 */
public record ContainerBinding(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String id,
        @JsonInclude(JsonInclude.Include.NON_NULL) String container,
        @JsonInclude(JsonInclude.Include.NON_NULL) String composeProject,
        @JsonInclude(JsonInclude.Include.NON_NULL) String composeService,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int port,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> instances) {

    /** The Binding with ID {@code id} that {@code binding} asks for, giving {@code instances}. */
    ContainerBinding(String id, ContainerBindingRequest binding, List<String> instances) {
        this(id, binding.container(), binding.composeProject(), binding.composeService(), binding.port(), instances);
    }

    /** The ID of the Binding {@code binding} asks for. */
    static String id(ContainerBindingRequest binding) {
        return binding.followsCompose()
                ? "compose:" + binding.composeProject() + ":" + binding.composeService() + ":" + binding.port()
                : binding.container() + ":" + binding.port();
    }

    /**
     * What it follows, as the pages say it: {@code container web-1}, or {@code Compose service web of project shop}.
     */
    public String follows() {
        return container != null ? "container " + container
                : "Compose service " + composeService + " of project " + composeProject;
    }
}
