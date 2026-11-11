package cc.lvhy.yard.dashboard.services;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * An Alias that adding made, or one that was there already, and whether adding created its Service.
 * Its JSON is the Alias with two more fields.
 *
 * @param serviceCreated true when the Service was missing, so it was created and published with
 *     Local Scope. False when the Alias joined an existing Service, or was there already.
 * @param aliasCreated true when the Alias is new. False when the Service had it already, and so
 *     nothing changed.
 */
public record AddedAlias(
    @JsonUnwrapped Alias alias,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean serviceCreated,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean aliasCreated) {}
