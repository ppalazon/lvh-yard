package dev.palazon.lvhyard.dashboard.services;

import java.util.List;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonValue;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Why other Workstations may not reach a Service that its Scope gives them a Route to.
 *
 * @param kind what is wrong, for a script to tell warnings apart
 * @param message what is wrong, in words, naming the Instance
 */
public record ServiceWarning(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Kind kind,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String message) {

    /** What a {@link ServiceWarning} is about. */
    public enum Kind {

        /** An Instance listens on a loopback address, which only its own Workstation can connect to. */
        LOOPBACK_INSTANCE,

        /** A Bound Instance is on a container port that is not published on the host. */
        UNPUBLISHED_CONTAINER_PORT;

        /** The kind's name in the API, such as {@code loopback-instance}. */
        @JsonValue
        public String value() {
            return name().toLowerCase().replace('_', '-');
        }
    }

    /**
     * The warnings of a Service with {@code publication}, or none, and these {@code instances}, when it has Yard
     * Scope: one for each Instance on a loopback address, and one for each Bound Instance on an unpublished container
     * port. A Local Service is reached only from its Home Workstation, where both work.
     */
    static List<ServiceWarning> of(Publication publication, List<Instance> instances) {
        if (publication == null || publication.scope() != Scope.YARD) {
            return List.of();
        }
        return Stream.concat(
                instances.stream()
                        .filter(Instance::onLoopback)
                        .map(instance -> new ServiceWarning(Kind.LOOPBACK_INSTANCE, "The Instance at "
                                + instance.address() + " is on a loopback address, so other Workstations cannot"
                                + " reach it. Register it at its Workstation's LAN address instead.")),
                instances.stream()
                        .filter(Instance::unpublishedContainerPort)
                        .map(instance -> new ServiceWarning(Kind.UNPUBLISHED_CONTAINER_PORT, "The Bound Instance at "
                                + instance.address() + " is on a container port that is not published on the host,"
                                + " so other Workstations cannot reach it. Publish the port, with docker run -p "
                                + instance.port() + " or a ports: entry in the Compose file, and restart the"
                                + " container.")))
                .toList();
    }
}
