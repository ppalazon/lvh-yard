package dev.palazon.lvhyard.dashboard.services;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * @param scope when left out, a new Publication gets Local Scope and an existing one keeps its
 *     Scope
 * @param priority the Instance Priority, each kind at most once. When left out, a new Publication
 *     gets static, bound, alias and an existing one keeps its order.
 */
public record PublicationRequest(
    Scope scope,
    @ArraySchema(schema = @Schema(allowableValues = {"static", "bound", "alias"}))
        List<String> priority) {}
