package dev.palazon.lvhyard.dashboard.services;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.List;

/** What an Instance Priority orders. */
public enum InstanceKind {
  STATIC,
  BOUND,

  /** The Remote Service of an Alias with the same name as the Service. */
  ALIAS;

  /** For a Publication that has no order of its own, such as one from before Instance Priority. */
  public static final List<InstanceKind> DEFAULT_PRIORITY = List.of(STATIC, BOUND, ALIAS);

  static InstanceKind of(Instance instance) {
    return instance.bound() ? BOUND : STATIC;
  }

  @JsonValue
  public String value() {
    return name().toLowerCase();
  }
}
