package dev.palazon.lvhyard.dashboard.backups;

import dev.palazon.lvhyard.dashboard.backups.ImportPlan.Step;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Imports a Backup into this Workstation. It checks the whole file before the first write, so a
 * refused file changes nothing. The API and the pages share it.
 */
@Component
public class BackupImport {

  private final ImportPlanner planner;

  private final BackupExport export;

  private final BackupsFolder folder;

  BackupImport(ImportPlanner planner, BackupExport export, BackupsFolder folder) {
    this.planner = planner;
    this.export = export;
    this.folder = folder;
  }

  /**
   * @param text the Backup's YAML
   * @param dryRun {@code true} plans and writes nothing
   * @return with the name of the safety Backup in the Backups folder, written before the first
   *     write
   * @throws RefusedBackupException with every problem of the file; then nothing changes
   * @throws ImportFailedException when a write fails; the writes before it stay
   */
  public synchronized ImportResult run(String text, ImportMode mode, boolean dryRun) {
    ImportPlan plan = planner.plan(BackupFormat.read(text), mode);
    String safetyBackup = null;
    if (!dryRun && plan.changes()) {
      safetyBackup = folder.writeNew("-before-import", BackupFormat.write(export.backup()));
      apply(plan);
    }
    return result(plan, dryRun, safetyBackup);
  }

  private static void apply(ImportPlan plan) {
    List<PlanEntry> applied = new ArrayList<>();
    for (Step step : plan.steps()) {
      try {
        step.write().run();
      } catch (RuntimeException e) {
        throw new ImportFailedException(applied, step.entries().getFirst(), e);
      }
      applied.addAll(step.entries());
    }
  }

  private static ImportResult result(ImportPlan plan, boolean dryRun, String safetyBackup) {
    Backup.ExportedFrom from = plan.backup().exportedFrom();
    return new ImportResult(
        from == null
            ? null
            : new ImportResult.ExportedFrom(
                from.workstation(),
                from.at() == null
                    ? null
                    : from.at().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)),
        plan.mode(),
        dryRun,
        plan.entries(),
        safetyBackup);
  }
}
