package dev.palazon.lvhyard.dashboard.services;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A Container Binding to add, which is also how one is kept in Consul KV.
 *
 * @param container the name of the container to follow, as {@code docker run --name} takes it
 * @param port the container port the Service listens on, inside the container
 */
public record ContainerBindingRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String container,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Integer port) {
}
