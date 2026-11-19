package cc.lvhy.yard.dashboard.backups;

import cc.lvhy.yard.dashboard.backups.ImportPlan.Step;
import cc.lvhy.yard.dashboard.backups.PlanEntry.Kind;
import cc.lvhy.yard.dashboard.profiles.RouteProfileDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * One import's plan, as the planners of its parts build it. Each part adds its entries, its writes
 * and its problems here. The writes keep the order of {@link ImportPlan}: Route Profiles, then new
 * Services, then Static Instances and Container Bindings, then Publications, then Aliases, then the
 * Instance Priority.
 */
final class PlanDraft {

  static final String EXISTS = "It exists already.";

  static final String SAME = "It is the same already.";

  final Backup backup;

  final ImportMode mode;

  /** What this Workstation has now, in the same model as the file. */
  final Backup current;

  final Map<String, RouteProfileDefinition> storedProfiles;

  final String lanAddress;

  final List<PlanEntry> entries = new ArrayList<>();

  final List<Step> profileSteps = new ArrayList<>();

  final List<Step> serviceSteps = new ArrayList<>();

  final List<Step> instanceSteps = new ArrayList<>();

  final List<Step> publicationSteps = new ArrayList<>();

  final List<Step> aliasSteps = new ArrayList<>();

  final List<Step> prioritySteps = new ArrayList<>();

  private final List<BackupProblem> problems = new ArrayList<>();

  PlanDraft(
      Backup backup,
      ImportMode mode,
      Backup current,
      Map<String, RouteProfileDefinition> storedProfiles,
      String lanAddress) {
    this.backup = backup;
    this.mode = mode;
    this.current = current;
    this.storedProfiles = storedProfiles;
    this.lanAddress = lanAddress;
  }

  /**
   * Add when nothing matches. A match is a skip when it is the same, or in add mode, and else an
   * update.
   */
  PlanEntry entry(Kind kind, String name, String service, boolean matches, boolean same) {
    if (!matches) {
      return PlanEntry.add(kind, name, service);
    }
    if (same) {
      return PlanEntry.skip(kind, name, service, SAME);
    }
    return mode == ImportMode.UPDATE
        ? PlanEntry.update(kind, name, service)
        : PlanEntry.skip(kind, name, service, EXISTS);
  }

  void problem(String place, String reason) {
    problems.add(new BackupProblem(place, reason));
  }

  /**
   * @throws RefusedBackupException with every problem of the file; then nothing is planned
   */
  ImportPlan plan() {
    if (!problems.isEmpty()) {
      throw new RefusedBackupException(problems);
    }
    List<Step> steps = new ArrayList<>(profileSteps);
    steps.addAll(serviceSteps);
    steps.addAll(instanceSteps);
    steps.addAll(publicationSteps);
    steps.addAll(aliasSteps);
    steps.addAll(prioritySteps);
    return new ImportPlan(backup, mode, entries, steps);
  }
}
