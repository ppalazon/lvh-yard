package dev.palazon.lvhyard.dashboard.status;

/**
 * Each one is a Spring bean, read side by side in {@link
 * org.springframework.core.annotation.Order}. A new part is a new bean, and nothing else changes.
 */
public interface PartReader {

  /** Shown as it is, and the same in every answer. */
  String name();

  /** It may throw or take too long. The part then shows as failing, with the reason. */
  Part read();
}
