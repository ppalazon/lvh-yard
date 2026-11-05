package dev.palazon.lvhyard.dashboard.backups;

import dev.palazon.lvhyard.dashboard.backups.ImportPlan.Step;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
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
    return dryRun ? result(plan, true, null) : apply(plan);
  }

  /**
   * Imports only the plan that the person saw. The file or the setup can change between the dry run
   * and the confirmation.
   *
   * @param fingerprint the {@link #fingerprint} of the dry run that the person confirmed
   * @throws PlanChangedException with the new plan when it differs; then nothing changes
   * @throws RefusedBackupException with every problem of the file; then nothing changes
   * @throws ImportFailedException when a write fails; the writes before it stay
   */
  public synchronized ImportResult runConfirmed(String text, ImportMode mode, String fingerprint) {
    ImportPlan plan = planner.plan(BackupFormat.read(text), mode);
    ImportResult now = result(plan, true, null);
    if (!fingerprint(text, now).equals(fingerprint)) {
      throw new PlanChangedException(now);
    }
    return apply(plan);
  }

  /** Differs when the file, the mode or any plan entry differs. */
  public static String fingerprint(String text, ImportResult plan) {
    try {
      MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
      sha256.update(text.getBytes(StandardCharsets.UTF_8));
      sha256.update((plan.mode() + "\n" + plan.entries()).getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(sha256.digest());
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("Every Java runtime has SHA-256.", e);
    }
  }

  private ImportResult apply(ImportPlan plan) {
    if (!plan.changes()) {
      return result(plan, false, null);
    }
    String safetyBackup = folder.writeNew("-before-import", BackupFormat.write(export.backup()));
    List<PlanEntry> applied = new ArrayList<>();
    for (Step step : plan.steps()) {
      try {
        step.write().run();
      } catch (RuntimeException e) {
        throw new ImportFailedException(applied, step.entries().getFirst(), safetyBackup, e);
      }
      applied.addAll(step.entries());
    }
    return result(plan, false, safetyBackup);
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
