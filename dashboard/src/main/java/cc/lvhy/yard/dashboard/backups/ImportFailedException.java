package cc.lvhy.yard.dashboard.backups;

import java.util.List;

/**
 * A write failed halfway. Consul has no transaction across the catalog and KV, so what the import
 * wrote before stays.
 */
public class ImportFailedException extends RuntimeException {

  private final List<PlanEntry> applied;

  private final String safetyBackup;

  ImportFailedException(
      List<PlanEntry> applied, PlanEntry failed, String safetyBackup, RuntimeException cause) {
    super("The import stopped at " + failed.what() + ": " + cause.getMessage(), cause);
    this.applied = List.copyOf(applied);
    this.safetyBackup = safetyBackup;
  }

  /** What the import wrote before the failure, in the order it wrote them. */
  public List<PlanEntry> applied() {
    return applied;
  }

  /** The file in the Backups folder with the setup from before the import. */
  public String safetyBackup() {
    return safetyBackup;
  }
}
