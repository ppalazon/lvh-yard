package cc.lvhy.yard.dashboard.backups;

/** The Backups folder has no Backup of that name. */
public class NoBackupException extends RuntimeException {

  NoBackupException(String name) {
    super("The Backups folder has no Backup called " + name + ".");
  }
}
