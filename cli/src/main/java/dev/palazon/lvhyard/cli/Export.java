package dev.palazon.lvhyard.cli;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

@Command(
    name = "export",
    mixinStandardHelpOptions = true,
    description = "Prints a Backup of this Workstation's setup as YAML.")
class Export implements Runnable {

  private static final Set<PosixFilePermission> OWNER_ONLY =
      PosixFilePermissions.fromString("rw-------");

  @ParentCommand Yard yard;

  @Option(
      names = {"-o", "--output"},
      paramLabel = "<file>",
      description = "Writes the Backup to this file, which only you can read.")
  Path output;

  @Spec CommandSpec spec;

  @Override
  public void run() {
    String backup = yard.dashboard().backup();
    PrintWriter out = spec.commandLine().getOut();
    if (output == null) {
      out.print(backup);
    } else {
      write(backup);
      out.println("Wrote the Backup to " + output + ".");
    }
    out.flush();
  }

  /** The file is owner-only before the Backup is in it, as the Backup holds basic-auth hashes. */
  private void write(String backup) {
    try {
      try {
        Files.createFile(output, PosixFilePermissions.asFileAttribute(OWNER_ONLY));
      } catch (FileAlreadyExistsException exists) {
        Files.setPosixFilePermissions(output, OWNER_ONLY);
      } catch (UnsupportedOperationException notPosix) {
        // A file system without POSIX permissions keeps its own.
      }
      Files.writeString(
          output,
          backup,
          StandardCharsets.UTF_8,
          StandardOpenOption.CREATE,
          StandardOpenOption.TRUNCATE_EXISTING,
          StandardOpenOption.WRITE);
    } catch (IOException e) {
      throw new YardException("Cannot write " + output + ": " + e.getMessage());
    }
  }
}
