package cc.lvhy.yard.dashboard.status;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Collection;

/** From best to worst. */
public enum PartState {
  OK,

  /** Such as a teammate missing from the WAN pool. */
  WARNING,

  FAILING;

  /** OK when there are none. */
  public static PartState worst(Collection<PartState> states) {
    return states.stream().reduce(OK, PartState::worse);
  }

  public PartState worse(PartState other) {
    return other.ordinal() > ordinal() ? other : this;
  }

  @JsonValue
  public String value() {
    return name().toLowerCase();
  }
}
