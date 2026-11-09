package dev.palazon.lvhyard.dashboard.services;

import com.fasterxml.jackson.annotation.JsonValue;

/** What kind of thing an Instance Source is. The order of Instance Sources never depends on it. */
public enum SourceKind {
  STATIC,

  /** A Container Binding, which gives Bound Instances. */
  BOUND,

  /** An Alias, which gives the Instances of its Remote Service. */
  ALIAS,

  FOREIGN;

  @JsonValue
  public String value() {
    return name().toLowerCase();
  }
}
