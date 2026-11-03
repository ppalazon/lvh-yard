package dev.palazon.lvhyard.dashboard.status;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Collection;

/** How one part of LVH Yard on this Workstation is, from best to worst. */
public enum PartState {

  /** It works. */
  OK,

  /** It works, but something needs a look, such as a teammate missing from the WAN pool. */
  WARNING,

  /** It does not work. */
  FAILING;

  /** The worst of {@code states}, or OK when there are none. */
  public static PartState worst(Collection<PartState> states) {
    return states.stream().reduce(OK, PartState::worse);
  }

  /** The worse of this state and {@code other}. */
  public PartState worse(PartState other) {
    return other.ordinal() > ordinal() ? other : this;
  }

  /** The state's name in the API, such as {@code warning}. */
  @JsonValue
  public String value() {
    return name().toLowerCase();
  }
}
