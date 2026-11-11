package cc.lvhy.yard.dashboard.services;

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

  /** Some Instances pass and some do not. */
  DEGRADED,

  /** Every Instance passes. */
  HEALTHY;

  static ServiceState of(Publication publication, List<Instance> instances) {
    if (publication == null) {
      return UNPUBLISHED;
    }
    if (instances.isEmpty()) {
      return UNAVAILABLE;
    }
    long passing =
        instances.stream().filter(instance -> instance.health() == Health.PASSING).count();
    if (passing == 0) {
      return UNHEALTHY;
    }
    return passing == instances.size() ? HEALTHY : DEGRADED;
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
