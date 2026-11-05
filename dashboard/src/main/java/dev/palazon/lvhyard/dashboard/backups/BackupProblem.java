package dev.palazon.lvhyard.dashboard.backups;

/**
 * @param place where in the file, such as {@code services.grafana.static-instances[0].port}; empty
 *     for the whole file
 */
public record BackupProblem(String place, String reason) {}
