package dev.palazon.lvhyard.dashboard.backups;

import java.util.List;

/**
 * A write failed halfway. Consul has no transaction across the catalog and KV, so what the import
 * wrote before stays.
 */
public class ImportFailedException extends RuntimeException {

  private final List<PlanEntry> applied;

  ImportFailedException(List<PlanEntry> applied, PlanEntry failed, RuntimeException cause) {
    super(
        "The import stopped at the "
            + failed.kind().value().replace('-', ' ')
            + " "
            + failed.name()
            + (failed.service() == null ? "" : " of " + failed.service())
            + ": "
            + cause.getMessage()
            + " It applied "
            + applied.size()
            + (applied.size() == 1 ? " change" : " changes")
            + " before that.",
        cause);
    this.applied = List.copyOf(applied);
  }

  /** What the import wrote before the failure, in the order it wrote them. */
  public List<PlanEntry> applied() {
    return applied;
  }
}
