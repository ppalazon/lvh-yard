package dev.palazon.lvhyard.dashboard.services;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * @param scope when left out, a new Publication gets Local Scope and an existing one keeps its
 *     Scope
 * @param priority the Instance Priority, each kind at most once. Empty turns it off. When left out,
 *     a new Publication gets static, bound, alias and an existing one keeps its order.
 * @param profiles names of this Workstation's Route Profiles, in the order the Route applies them.
 *     Empty removes them all. When left out, a Publication keeps its own.
 */
public record PublicationRequest(
    Scope scope,
    @ArraySchema(schema = @Schema(allowableValues = {"static", "bound", "alias"}))
        List<String> priority,
    @ArraySchema(
            arraySchema =
                @Schema(
                    description =
                        "Names of this Workstation's Route Profiles, in the order the Route applies"
                            + " them. Empty removes them all. When left out, a Publication keeps"
                            + " its own."))
        List<String> profiles) {}
