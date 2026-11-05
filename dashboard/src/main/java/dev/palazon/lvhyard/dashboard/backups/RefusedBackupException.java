package dev.palazon.lvhyard.dashboard.backups;

import java.util.List;

/** The file is not a Backup this dashboard can import, so nothing changes. */
public class RefusedBackupException extends RuntimeException {

  private final List<BackupProblem> problems;

  public RefusedBackupException(List<BackupProblem> problems) {
    super(
        "The Backup is refused, and nothing changes: "
            + String.join(" ", problems.stream().map(BackupProblem::reason).toList()));
    this.problems = List.copyOf(problems);
  }

  public List<BackupProblem> problems() {
    return problems;
  }
}
