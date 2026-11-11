package cc.lvhy.yard.dashboard.backups;

/** The plan differs from the one the person confirmed, so nothing changes. */
public class PlanChangedException extends RuntimeException {

  private final ImportResult plan;

  PlanChangedException(ImportResult plan) {
    super("The plan changed since you saw it, so nothing changed.");
    this.plan = plan;
  }

  /** The dry run of now, for a new confirmation. */
  public ImportResult plan() {
    return plan;
  }
}
