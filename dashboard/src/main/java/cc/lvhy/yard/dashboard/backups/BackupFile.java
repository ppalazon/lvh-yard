package cc.lvhy.yard.dashboard.backups;

import java.util.List;

/**
 * One file of the Backups folder, as read.
 *
 * @param backup {@code null} when the file is not a Backup
 * @param problems why the file is not a Backup; empty when it is one
 */
public record BackupFile(String name, Backup backup, List<BackupProblem> problems) {

  public BackupFile {
    problems = List.copyOf(problems);
  }
}
