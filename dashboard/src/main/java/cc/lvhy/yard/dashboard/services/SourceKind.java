package cc.lvhy.yard.dashboard.services;

import com.fasterxml.jackson.annotation.JsonValue;

/** What kind of thing an Instance Source is. The order of Instance Sources never depends on it. */
public enum SourceKind {
  STATIC,

  /** A Container Binding, which gives Bound Instances. */
  BOUND,

  /** An Alias, which gives the Instances of its Remote Service. */
  ALIAS,

  FOREIGN;

  /**
   * How pages and messages name a source of this kind, such as {@code Static Instance 10.0.0.5:80}.
   *
   * @param detail what tells it apart: the address of an Instance, the container of a Container
   *     Binding, or the Remote Service of an Alias
   */
  public String describe(String detail) {
    return switch (this) {
      case STATIC -> "Static Instance " + detail;
      case BOUND -> "Container Binding " + detail;
      case ALIAS -> "Alias to " + detail;
      case FOREIGN -> "Foreign Instance " + detail;
    };
  }

  @JsonValue
  public String value() {
    return name().toLowerCase();
  }
}
