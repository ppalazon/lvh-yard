package dev.palazon.lvhyard.dashboard.services;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * It covers every Instance of the Service, including ones registered later.
 *
 * @param priority the Instance Priority. It is left out of the JSON of the Publication, because the
 *     Service already shows it as its own {@code priority} field.
 * @param profiles the names of its Route Profiles, in the order its Route applies them. Left out of
 *     the JSON for the same reason.
 */
public record Publication(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Scope scope,
    @JsonIgnore @Schema(hidden = true) List<InstanceKind> priority,
    @JsonIgnore @Schema(hidden = true) List<String> profiles) {

  public Publication {
    profiles = List.copyOf(profiles);
  }

  Publication(Scope scope) {
    this(scope, InstanceKind.DEFAULT_PRIORITY, List.of());
  }
}
