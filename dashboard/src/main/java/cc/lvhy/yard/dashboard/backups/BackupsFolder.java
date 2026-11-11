package cc.lvhy.yard.dashboard.backups;

import cc.lvhy.yard.dashboard.Workstation;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Stream;
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
      } catch (FileAlreadyExistsException taken) {
        continue;
      }
    }
  }

  /**
   * Writes {@code text} to a new file {@code name}, with mode {@code 0600} from the start.
   *
   * @throws FileAlreadyExistsException when {@code name} exists; then the file stays as it was
   */
  public void write(String name, String text) throws FileAlreadyExistsException {
    String problem = nameProblem(name);
    if (problem != null) {
      throw new IllegalArgumentException(problem);
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

  /**
   * Each {@code .yaml} and {@code .yml} file, by name, read as a Backup. A missing folder has none.
   */
  public List<BackupFile> list() {
    if (!Files.isDirectory(path)) {
      return List.of();
    }
    try (Stream<Path> files = Files.list(path)) {
      return files
          .filter(Files::isRegularFile)
          .map(file -> file.getFileName().toString())
          .filter(name -> nameProblem(name) == null)
          .sorted()
          .map(this::read)
          .toList();
    } catch (IOException e) {
      throw new UncheckedIOException(
          "Cannot list the Backups folder " + path + ": " + e.getMessage(), e);
    }
  }

  /**
   * @throws NoBackupException when the folder has no Backup {@code name}
   */
  public BackupFile read(String name) {
    String text = text(name);
    try {
      return new BackupFile(name, BackupFormat.read(text), List.of());
    } catch (RefusedBackupException e) {
      return new BackupFile(name, null, e.problems());
    }
  }

  /**
   * The file as it is, such as for a download.
   *
   * @throws NoBackupException when the folder has no Backup {@code name}
   */
  public String text(String name) {
    try {
      return Files.readString(existing(name));
    } catch (NoSuchFileException e) {
      throw new NoBackupException(name);
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot read the Backup " + name + ": " + e.getMessage(), e);
    }
  }

  /**
   * @throws NoBackupException when the folder has no Backup {@code name}
   */
  public void delete(String name) {
    try {
      Files.delete(existing(name));
    } catch (NoSuchFileException e) {
      throw new NoBackupException(name);
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot delete the Backup " + name + ": " + e.getMessage(), e);
    }
  }

  /** A name from outside, such as from an address, names only a Backup file of the folder. */
  private Path existing(String name) {
    if (nameProblem(name) != null || !Files.isRegularFile(path.resolve(name))) {
      throw new NoBackupException(name);
    }
    return path.resolve(name);
  }

  /**
   * A Backup file is in the folder itself, is not hidden, and is YAML.
   *
   * @return why {@code name} cannot name a Backup file, or {@code null} when it can
   */
  public static String nameProblem(String name) {
    if (name.isEmpty()) {
      return "The file has no name.";
    }
    if (name.contains("/") || name.contains("\\")) {
      return "The file name " + name + " has a folder in it.";
    }
    if (name.startsWith(".")) {
      return "The file name " + name + " starts with a dot.";
    }
    if (!name.endsWith(".yaml") && !name.endsWith(".yml")) {
      return "The file name " + name + " does not end with .yaml or .yml.";
    }
    return null;
  }
}
