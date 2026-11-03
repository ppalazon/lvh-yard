package dev.palazon.lvhyard.dashboard.services;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.List;

public enum ServiceState {

  /** By choice. Its Instances may still be registered and healthy. */
  UNPUBLISHED,

  /** No registered Instances. */
  UNAVAILABLE,

  /** No passing Instance. */
  UNHEALTHY,

  /** At least one passing Instance. */
  HEALTHY;

  static ServiceState of(Publication publication, List<Instance> instances) {
    if (publication == null) {
      return UNPUBLISHED;
    }
    if (instances.isEmpty()) {
      return UNAVAILABLE;
    }
    return instances.stream().anyMatch(instance -> instance.health() == Health.PASSING)
        ? HEALTHY
        : UNHEALTHY;
  }

  public static ServiceState parse(String value) {
    return Arrays.stream(values())
        .filter(state -> state.value().equals(value))
        .findFirst()
        .orElseThrow(() -> new UnknownStateException(value));
  }

  @JsonValue
  public String value() {
    return name().toLowerCase();
  }
}
