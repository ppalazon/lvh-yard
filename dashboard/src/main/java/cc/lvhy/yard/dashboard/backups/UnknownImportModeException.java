package cc.lvhy.yard.dashboard.backups;

public class UnknownImportModeException extends RuntimeException {

  UnknownImportModeException(String mode) {
    super("The import mode " + mode + " is not one LVH Yard has. Use add.");
  }
}
