package dev.palazon.lvhyard.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@code lvh-yard init --reinit}: the settings of a folder's old {@code .env}, carried over to the
 * new one, and the backups of the old files.
 */
final class Reinit {

  private final Path folder;

  /** The old {@code .env}'s settings that are not commented, by key. */
  private final Map<String, String> settings;

  private Reinit(Path folder, Map<String, String> settings) {
    this.folder = folder;
    this.settings = settings;
  }

  /**
   * The old settings of the {@code .env} in {@code folder}.
   *
   * @throws YardException when {@code folder} has no {@code .env}, or it cannot be read
   */
  static Reinit of(Path folder) {
    Path file = folder.resolve(Templates.ENV);
    if (!Files.exists(file)) {
      throw new YardException(
          "No "
              + file
              + " to carry values from: nothing was written. Run lvh-yard init without --reinit to"
              + " write new files.");
    }
    try {
      return new Reinit(folder, EnvFile.parse(Files.readString(file)).settings());
    } catch (IOException e) {
      throw new YardException("Could not read " + file + ": " + e.getMessage());
    }
  }

  /** The old value of {@code key}, when it was set and not empty. */
  Optional<String> oldValue(String key) {
    return Optional.ofNullable(settings.get(key)).filter(value -> !value.isEmpty());
  }

  /**
   * Sets each old setting that {@code env} has; the others are kept at the end, commented, so
   * nothing the user wrote is lost.
   */
  void carryOver(EnvFile env) {
    List<String> gone = new ArrayList<>();
    settings.forEach((key, value) -> {
      if (env.has(key)) {
        env.set(key, value);
      } else {
        gone.add("#" + key + "=" + value);
      }
    });
    if (!gone.isEmpty()) {
      env.append("").append("# No longer settings of lvh-yard, kept from the old .env:");
      gone.forEach(env::append);
    }
  }

  /**
   * Renames {@code compose.yaml} and {@code .env}, those that exist, to {@code
   * <file>.<yyyyMMdd-HHmmss>.bak}.
   *
   * @return each file's backup, by the file's name
   */
  Map<String, Path> backUp() {
    String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
    Map<String, Path> backups = new LinkedHashMap<>();
    for (String name : List.of(Templates.COMPOSE, Templates.ENV)) {
      Path file = folder.resolve(name);
      if (Files.exists(file)) {
        Path backup = folder.resolve(name + "." + now + ".bak");
        try {
          Files.move(file, backup);
        } catch (IOException e) {
          throw new YardException("Could not back up " + file + " to " + backup + ": " + e);
        }
        backups.put(name, backup);
      }
    }
    return backups;
  }
}
