package dev.palazon.lvhyard.dashboard.reachability;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Whether a request to one of the Service's hostnames, through this Workstation's Traefik, reached
 * the Service.
 *
 * @param hostname the hostname the request was for
 * @param reached whether Traefik passed the request on to an Instance, which answered
 * @param status the HTTP status of the answer; {@code null}, and left out of the JSON, when Traefik
 *     did not answer
 * @param error why the request did not reach the Service; {@code null}, and left out of the JSON,
 *     when it did
 */
public record RouteReachability(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String hostname,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean reached,
    @JsonInclude(JsonInclude.Include.NON_NULL) Integer status,
    @JsonInclude(JsonInclude.Include.NON_NULL) String error) {}
