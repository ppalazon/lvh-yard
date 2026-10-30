package dev.palazon.lvhyard.dashboard.services;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A link from a Service to a Docker container by its name, and a container port. While a container of that name
 * runs, the Service has a Bound Instance at its current address and port.
 *
 * @param id the Binding's ID, {@code <container>:<port>}, which the Service's Container Bindings path names it by
 * @param container the name of the container it follows
 * @param port the container port the Service listens on, inside the container
 * @param instances the IDs of the Bound Instances it gives the Service now; none while no container of that name
 *        runs
 */
public record ContainerBinding(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String container,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int port,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> instances) {

    /** The ID of the Binding to {@code container}'s {@code port}. */
    static String id(String container, int port) {
        return container + ":" + port;
    }
}
