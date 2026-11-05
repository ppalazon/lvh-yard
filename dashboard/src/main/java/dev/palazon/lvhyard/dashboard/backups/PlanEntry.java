package dev.palazon.lvhyard.dashboard.backups;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * What an import does with one entry of a Backup.
 *
 * @param name the Service, Alias or Route Profile name; {@code host:port} for a Static Instance;
 *     the ID for a Container Binding
 * @param service the Service of a Static Instance or a Container Binding, else {@code null}
 * @param reason why it is skipped, else {@code null}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PlanEntry(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Kind kind,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
    String service,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Action action,
    String reason) {

  static PlanEntry add(Kind kind, String name, String service) {
    return new PlanEntry(kind, name, service, Action.ADD, null);
  }

  static PlanEntry update(Kind kind, String name, String service) {
    return new PlanEntry(kind, name, service, Action.UPDATE, null);
  }

  static PlanEntry skip(Kind kind, String name, String service, String reason) {
    return new PlanEntry(kind, name, service, Action.SKIP, reason);
  }

  public enum Kind {
    ROUTE_PROFILE,
    SERVICE,
    PUBLICATION,
    STATIC_INSTANCE,
    CONTAINER_BINDING,
    ALIAS;

    /** Such as {@code static-instance}. */
    @JsonValue
    public String value() {
      return name().toLowerCase().replace('_', '-');
    }
  }

  public enum Action {
    ADD,
    UPDATE,
    SKIP;

    @JsonValue
    public String value() {
      return name().toLowerCase();
    }
  }
}
