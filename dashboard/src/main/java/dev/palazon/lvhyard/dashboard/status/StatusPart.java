package dev.palazon.lvhyard.dashboard.status;

/**
 * One part of the status, such as Consul or Docker. Each is a Spring bean; {@link StatusReader}
 * reads every one, side by side, in the order their {@link
 * org.springframework.core.annotation.Order} gives. A new part is a new bean: nothing else changes.
 */
public interface StatusPart {

  /** The part's name, such as {@code Consul}, shown as it is and the same in every answer. */
  String name();

  /**
   * How the part is now. It may throw, or take too long: the reader then shows the part as failing,
   * with the reason.
   */
  Part read();
}
