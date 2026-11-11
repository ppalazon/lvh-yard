package cc.lvhy.yard.dashboard.backups;

import cc.lvhy.yard.dashboard.services.SourceKind;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * What an import does with one entry of a Backup.
 *
 * @param name the Service or Route Profile name; {@code host:port} for a Static Instance; the ID
 *     for a Container Binding; {@code <workstation>/<service>} for an Alias
 * @param service the Service of a Static Instance, a Container Binding or an Alias, else {@code
 *     null}
 * @param reason why it is skipped, else {@code null}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PlanEntry(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Kind kind,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
    String service,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Action action,
    String reason) {

  /**
   * Such as {@code Static Instance 10.1.2.3:8080 of web} or {@code Publication of web}, in the
   * words of {@code lvh-yard import}.
   */
  public String what() {
    String what =
        switch (kind) {
          case ROUTE_PROFILE -> "Route Profile " + name;
          case SERVICE -> "Service " + name;
          case PUBLICATION -> "Publication of " + name;
          case INSTANCE_PRIORITY -> "Instance Priority of " + name;
          case STATIC_INSTANCE -> SourceKind.STATIC.describe(name);
          case CONTAINER_BINDING -> SourceKind.BOUND.describe(name);
          case ALIAS -> SourceKind.ALIAS.describe(name);
        };
    return what + (service == null ? "" : " of " + service);
  }

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
    INSTANCE_PRIORITY,
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
