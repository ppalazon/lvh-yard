package cc.lvhy.yard.dashboard.reachability;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * {@code status}, {@code note} and {@code error} are left out of the JSON when null.
 *
 * @param scheme {@code http} or {@code https}: the Traefik listener that was asked
 * @param note what a check that reached the Route says in place of a status, when no HTTP answer
 *     came
 */
public record RouteReachability(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String scheme,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String hostname,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean reached,
    @JsonInclude(JsonInclude.Include.NON_NULL) Integer status,
    @JsonInclude(JsonInclude.Include.NON_NULL) String note,
    @JsonInclude(JsonInclude.Include.NON_NULL) String error) {}
