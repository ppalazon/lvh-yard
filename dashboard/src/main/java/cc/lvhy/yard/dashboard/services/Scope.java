package cc.lvhy.yard.dashboard.services;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Optional;

public enum Scope {

  /** At {@code <service>.<base-domain>}. */
  LOCAL,

  /** The other Workstations route it at {@code <service>.<workstation>.<base-domain>}. */
  YARD;

  static Scope of(String value) {
    return named(value)
        .orElseThrow(
            () -> new IllegalStateException("A Publication has the unknown Scope " + value + "."));
  }

  public static Optional<Scope> named(String value) {
    return Arrays.stream(values()).filter(scope -> scope.value().equals(value)).findFirst();
  }

  /** Also its value in Consul KV. */
  @JsonValue
  public String value() {
    return name().toLowerCase();
  }
}
