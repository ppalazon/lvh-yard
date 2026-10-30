package dev.palazon.lvhyard.dashboard.services;

import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonValue;

/** What a Service's Publication and the Health of its Instances add up to. */
public enum ServiceState {

    /** No Publication, by choice; its Instances may be registered and healthy. */
    UNPUBLISHED,

    /** Published, with no registered Instances. */
    UNAVAILABLE,

    /** Published, and none of its Instances is passing. */
    UNHEALTHY,

    /** Published, with at least one passing Instance: published and healthy. */
    HEALTHY;

    /** The state of a Service with {@code publication}, or none, and these registered {@code instances}. */
    static ServiceState of(Publication publication, List<Instance> instances) {
        if (publication == null) {
            return UNPUBLISHED;
        }
        if (instances.isEmpty()) {
            return UNAVAILABLE;
        }
        return instances.stream().anyMatch(instance -> instance.health() == Health.PASSING) ? HEALTHY : UNHEALTHY;
    }

    /** The state whose {@link #value} is {@code value}, such as {@code healthy}. */
    public static ServiceState parse(String value) {
        return Arrays.stream(values()).filter(state -> state.value().equals(value)).findFirst()
                .orElseThrow(() -> new UnknownStateException(value));
    }

    /** The state's name in the API, such as {@code unhealthy}. */
    @JsonValue
    public String value() {
        return name().toLowerCase();
    }
}
