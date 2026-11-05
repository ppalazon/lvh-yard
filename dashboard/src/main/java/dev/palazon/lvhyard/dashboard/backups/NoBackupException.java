package dev.palazon.lvhyard.dashboard.backups;

/** The Backups folder has no Backup of that name. */
public class NoBackupException extends RuntimeException {

  NoBackupException(String name) {
    super("The Backups folder has no Backup called " + name + ".");
  }
}
