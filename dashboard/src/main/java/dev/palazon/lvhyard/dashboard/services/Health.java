package dev.palazon.lvhyard.dashboard.services;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Collection;

/**
 * The result of an Instance's checks, from best to worst. An Instance's Health is its worst
 * check's.
 */
public enum Health {
  PASSING,
  WARNING,
  CRITICAL;

  /** Consul's name for a check status. Maintenance, or anything unknown, counts as critical. */
  static Health ofConsulStatus(String status) {
    return switch (status) {
      case "passing" -> PASSING;
      case "warning" -> WARNING;
      default -> CRITICAL;
    };
  }

  /** The worst of {@code healths}; passing when there are none. */
  static Health worst(Collection<Health> healths) {
    return healths.stream().max(Enum::compareTo).orElse(PASSING);
  }

  @JsonValue
  public String value() {
    return name().toLowerCase();
  }
}
