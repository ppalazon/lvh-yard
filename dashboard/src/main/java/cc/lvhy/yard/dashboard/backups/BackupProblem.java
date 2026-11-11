package cc.lvhy.yard.dashboard.backups;

/**
 * @param place where in the file, such as {@code services.grafana.sources[0].static-instance.port};
 *     empty for the whole file
 * @param reason a full sentence, without the place
 */
public record BackupProblem(String place, String reason) {

  /** Such as {@code services.grafana.sources[0].static-instance.port: The port is missing.} */
  public String text() {
    return place.isEmpty() ? reason : place + ": " + reason;
  }
}
