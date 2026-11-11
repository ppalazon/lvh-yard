package cc.lvhy.yard.dashboard.services;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * It covers every Instance of the Service, including ones registered later.
 *
 * @param profiles the names of its Route Profiles, in the order its Route applies them. It is left
 *     out of the JSON of the Publication, because the Service already shows it as its own {@code
 *     profiles} field.
 * @param accessLog whether Traefik writes a line in its access log for each request to its Route
 */
public record Publication(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Scope scope,
    @JsonIgnore @Schema(hidden = true) List<String> profiles,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean accessLog) {

  public Publication {
    profiles = List.copyOf(profiles);
  }

  Publication(Scope scope) {
    this(scope, List.of(), false);
  }
}
