package dev.palazon.lvhyard.dashboard.services;

/**
 * A registration that breaks a rule, such as a Service name that is not a single DNS label. The message explains
 * the rule to the person who asked.
 */
public class InvalidRegistrationException extends RuntimeException {

    private final String field;

    InvalidRegistrationException(String field, String message) {
        super(message);
        this.field = field;
    }

    /**
     * The field the rule is about: {@code name}, {@code host}, {@code port}, {@code check}, {@code path},
     * {@code interval}, {@code timeout}, {@code tags}, {@code meta}, or {@code workstation} for an Alias.
     */
    public String field() {
        return field;
    }
}
