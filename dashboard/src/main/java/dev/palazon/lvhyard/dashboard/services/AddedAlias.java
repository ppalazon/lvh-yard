package dev.palazon.lvhyard.dashboard.services;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A new Alias, and whether adding it created its Service. Its JSON is the Alias with one more
 * field.
 *
 * @param serviceCreated true when the Service was missing, so it was created and published with
 *     Local Scope. False when the Alias joined an existing Service.
 */
public record AddedAlias(
    @JsonUnwrapped Alias alias,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean serviceCreated) {}
