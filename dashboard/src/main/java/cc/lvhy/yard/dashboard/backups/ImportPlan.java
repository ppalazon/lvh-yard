package cc.lvhy.yard.dashboard.backups;

import java.util.List;

/**
 * What an import of {@code backup} would do. Every check has passed, so only a failed write can
 * stop it.
 *
 * @param entries for the person who imports, in the order of the file
 * @param steps the writes, in the order that keeps each one valid: Route Profiles, then new
 *     Services, then Static Instances and Container Bindings, then Publications, then Aliases
 */
record ImportPlan(Backup backup, ImportMode mode, List<PlanEntry> entries, List<Step> steps) {

  ImportPlan {
    entries = List.copyOf(entries);
    steps = List.copyOf(steps);
  }

  /** Whether the import writes anything at all. */
  boolean changes() {
    return !steps.isEmpty();
  }

  /**
   * One write. Several entries share one when one write does them all, such as the Route Profiles
   * that one Consul transaction saves.
   */
  record Step(List<PlanEntry> entries, Runnable write) {

    Step {
      entries = List.copyOf(entries);
    }
  }
}
