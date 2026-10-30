package dev.palazon.lvhyard.dashboard.services;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A Service's opt-in to a hostname. It covers every Instance of the Service, including ones registered later.
 *
 * @param scope who gets a Route for it
 */
public record Publication(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Scope scope) {
}
