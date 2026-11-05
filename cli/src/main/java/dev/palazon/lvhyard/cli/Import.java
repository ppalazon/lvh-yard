package dev.palazon.lvhyard.cli;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

@Command(
    name = "import",
    mixinStandardHelpOptions = true,
    description = "Adds what is new in a Backup to this Workstation, and skips what exists.")
class Import implements Runnable {

  @ParentCommand Yard yard;

  @Parameters(paramLabel = "<file>", description = "The Backup, or - for standard input.")
  String file;

  @Option(names = "--update", description = "Also overwrites what exists with the Backup.")
  boolean update;

  @Option(names = "--dry-run", description = "Shows the plan and changes nothing.")
  boolean dryRun;

  @Spec CommandSpec spec;

  @Override
  public void run() {
    Dashboard.ImportResult result =
        yard.dashboard().importBackup(text(), update ? "update" : "add", dryRun);
    PrintWriter out = spec.commandLine().getOut();
    Dashboard.ExportedFrom from = result.exportedFrom();
    if (from != null && from.workstation() != null) {
      out.println(
          "Backup of " + from.workstation() + (from.at() == null ? "" : " at " + from.at()) + ".");
    }
    int added = 0;
    int updated = 0;
    for (Dashboard.PlanEntry entry : result.entries()) {
      out.println(line(entry));
      switch (entry.action()) {
        case "add" -> added++;
        case "update" -> updated++;
        default -> {}
      }
    }
    int skipped = result.entries().size() - added - updated;
    if (result.dryRun()) {
      out.println("Dry run: nothing changed.");
    } else if (added + updated == 0) {
      out.println("Nothing changed: skipped " + skipped + ".");
    } else {
      out.println(
          "Added "
              + added
              + (updated == 0 ? "" : ", updated " + updated)
              + ", skipped "
              + skipped
              + ".");
    }
    if (result.safetyBackup() != null) {
      out.println("Safety Backup: " + result.safetyBackup() + ", in the Backups folder.");
    }
    out.flush();
  }

  /** Such as {@code add Static Instance 10.1.2.3:8080 of web}. */
  private static String line(Dashboard.PlanEntry entry) {
    String what =
        switch (entry.kind()) {
          case "route-profile" -> "Route Profile " + entry.name();
          case "service" -> "Service " + entry.name();
          case "publication" -> "Publication of " + entry.name();
          case "static-instance" -> "Static Instance " + entry.name();
          case "container-binding" -> "Container Binding " + entry.name();
          case "alias" -> "Alias " + entry.name();
          default -> entry.kind() + " " + entry.name();
        };
    return entry.action()
        + " "
        + what
        + (entry.service() == null ? "" : " of " + entry.service())
        + (entry.reason() == null ? "" : ": " + entry.reason());
  }

  private String text() {
    if (file.equals("-")) {
      try {
        return new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
      } catch (IOException e) {
        throw new YardException("Cannot read the standard input: " + e.getMessage());
      }
    }
    try {
      return Files.readString(Path.of(file));
    } catch (IOException e) {
      throw new YardException("Cannot read " + file + ": " + e.getMessage());
    }
  }
}
