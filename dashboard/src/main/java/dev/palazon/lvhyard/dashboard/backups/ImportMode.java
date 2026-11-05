package dev.palazon.lvhyard.dashboard.backups;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

/** What an import does with an entry that matches one on this Workstation. */
public enum ImportMode {

  /** It skips the match. */
  ADD;

  /**
   * @throws UnknownImportModeException when no mode has this value
   */
  static ImportMode of(String value) {
    return Arrays.stream(values())
        .filter(mode -> mode.value().equals(value))
        .findFirst()
        .orElseThrow(() -> new UnknownImportModeException(value));
  }

  @JsonValue
  public String value() {
    return name().toLowerCase();
  }
}
