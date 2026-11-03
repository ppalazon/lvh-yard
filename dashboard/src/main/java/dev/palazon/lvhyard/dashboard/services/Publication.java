package dev.palazon.lvhyard.dashboard.services;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * It covers every Instance of the Service, including ones registered later.
 *
 * @param priority the Service shows it, as its own {@code priority}
 */
public record Publication(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Scope scope,
    @JsonIgnore @Schema(hidden = true) List<InstanceKind> priority) {

  Publication(Scope scope) {
    this(scope, InstanceKind.DEFAULT_PRIORITY);
  }
}
