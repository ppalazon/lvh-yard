package dev.palazon.lvhyard.dashboard.services;

import java.net.InetAddress;
import java.util.List;
import java.util.Locale;

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
        LOOPBACK_INSTANCE;

        /** The kind's name in the API, such as {@code loopback-instance}. */
        @JsonValue
        public String value() {
            return name().toLowerCase().replace('_', '-');
        }
    }

    /**
     * The warnings of a Service with {@code publication}, or none, and these {@code instances}: one for each Instance
     * on a loopback address, when it has Yard Scope. A Local Service is reached only from its Home Workstation, where
     * loopback works.
     */
    static List<ServiceWarning> of(Publication publication, List<Instance> instances) {
        if (publication == null || publication.scope() != Scope.YARD) {
            return List.of();
        }
        return instances.stream()
                .filter(instance -> loopback(instance.host()))
                .map(instance -> new ServiceWarning(Kind.LOOPBACK_INSTANCE, "The Instance at " + instance.address()
                        + " is on a loopback address, so other Workstations cannot reach it. Register it at this"
                        + " Workstation's LAN address instead."))
                .toList();
    }

    /**
     * Whether {@code host} is a loopback address, such as {@code 127.0.0.1}, {@code ::1} or {@code localhost}. A
     * hostname is not looked up, so one that only resolves to loopback is not seen.
     */
    static boolean loopback(String host) {
        try {
            return InetAddress.ofLiteral(host).isLoopbackAddress();
        } catch (IllegalArgumentException notAnAddress) {
            String name = host.toLowerCase(Locale.ROOT);
            name = name.endsWith(".") ? name.substring(0, name.length() - 1) : name;
            return name.equals("localhost") || name.endsWith(".localhost");
        }
    }
}
