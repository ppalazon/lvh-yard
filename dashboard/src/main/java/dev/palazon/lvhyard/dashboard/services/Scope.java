package dev.palazon.lvhyard.dashboard.services;

import com.fasterxml.jackson.annotation.JsonValue;

/** Who gets a Route for a Publication. For now only Local: the Home Workstation. */
public enum Scope {

    LOCAL;

    /** The Scope stored as {@code value}. */
    static Scope of(String value) {
        for (Scope scope : values()) {
            if (scope.value().equals(value)) {
                return scope;
            }
        }
        throw new IllegalStateException("A Publication has the unknown Scope " + value + ".");
    }

    /** The Scope's name in the API and in Consul KV, such as {@code local}. */
    @JsonValue
    public String value() {
        return name().toLowerCase();
    }
}
