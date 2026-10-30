package dev.palazon.lvhyard.dashboard.services;

import java.util.Arrays;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonValue;

/** Who gets a Route for a Publication: only the Home Workstation (Local), or every Workstation of the Yard. */
public enum Scope {

    /** Only the Home Workstation, at {@code <service>.<base-domain>}. */
    LOCAL,

    /**
     * Every Workstation of the Yard: the Home Workstation at {@code <service>.<base-domain>}, every other one at
     * {@code <service>.<workstation>.<base-domain>}.
     */
    YARD;

    /** The Scope stored as {@code value}. */
    static Scope of(String value) {
        return named(value).orElseThrow(
                () -> new IllegalStateException("A Publication has the unknown Scope " + value + "."));
    }

    /** The Scope whose {@link #value} is {@code value}, such as {@code yard}, if there is one. */
    public static Optional<Scope> named(String value) {
        return Arrays.stream(values()).filter(scope -> scope.value().equals(value)).findFirst();
    }

    /** The Scope's name in the API and in Consul KV, such as {@code local}; the API reads it back too. */
    @JsonValue
    public String value() {
        return name().toLowerCase();
    }
}
