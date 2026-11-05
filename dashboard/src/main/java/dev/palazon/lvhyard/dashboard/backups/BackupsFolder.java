package dev.palazon.lvhyard.dashboard.backups;

import dev.palazon.lvhyard.dashboard.Workstation;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

/**
 * The Backups folder: {@code backups/} in the Workstation Folder. Each file in it is one Backup,
 * and only its owner can read it, because a Backup holds basic-auth hashes.
 */
@Component
public class BackupsFolder {

  private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

  private final Path path;

  private final Workstation workstation;

  BackupsFolder(BackupsSettings settings, Workstation workstation) {
    this.path = settings.path();
    this.workstation = workstation;
  }

  /**
   * Writes {@code text} to a new file {@code <workstation>-<yyyyMMdd-HHmmss><suffix>.yaml}. If a
   * Backup of the same second has that name, a number follows the suffix, such as {@code -2}.
   *
   * @param suffix such as {@code -before-import}, or empty
   * @return the file name
   */
  public String writeNew(String suffix, String text) {
    String stem = workstation.name() + "-" + LocalDateTime.now().format(TIME) + suffix;
    for (int number = 1; ; number++) {
      String name = stem + (number == 1 ? "" : "-" + number) + ".yaml";
      try {
        write(name, text);
        return name;
      } catch (FileAlreadyExistsException e) {
        // The next number.
      }
    }
  }

  /**
   * Writes {@code text} to a new file {@code name}, with mode {@code 0600} from the start.
   *
   * @throws FileAlreadyExistsException when {@code name} exists; then the file stays as it was
   */
  public void write(String name, String text) throws FileAlreadyExistsException {
    if (name.contains("/") || name.contains("\\") || name.startsWith(".")) {
      throw new IllegalArgumentException("A Backup name has no path: " + name);
    }
    Path file = path.resolve(name);
    try {
      Files.createDirectories(path);
      Files.write(
          Files.createFile(
              file,
              PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))),
          text.getBytes(StandardCharsets.UTF_8),
          StandardOpenOption.TRUNCATE_EXISTING);
    } catch (FileAlreadyExistsException e) {
      throw e;
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot write the Backup " + file + ": " + e.getMessage(), e);
    }
  }
}
