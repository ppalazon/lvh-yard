package cc.lvhy.yard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;

/** The files of the Backups folder, which the tests of the Backups page make and delete. */
abstract class BackupFilesComponentTest extends DashboardComponentTest {

  /** Every test class of this JVM shares the Backups folder, so each test names its own files. */
  final List<Path> files = new ArrayList<>();

  @AfterEach
  void deleteTheFiles() throws IOException {
    for (Path file : files) {
      Files.deleteIfExists(file);
    }
  }

  static String alert(String page) {
    return div(page, "role=\"alert\"");
  }

  static Set<String> names() throws IOException {
    try (Stream<Path> files = Files.list(BACKUPS)) {
      return files
          .map(file -> file.getFileName().toString())
          .collect(Collectors.toCollection(HashSet::new));
    }
  }

  String newFile(String extension) {
    String name = "page-" + UUID.randomUUID() + extension;
    files.add(BACKUPS.resolve(name));
    return name;
  }

  static void write(String name, String text) throws IOException {
    Files.createDirectories(BACKUPS);
    Files.writeString(BACKUPS.resolve(name), text);
  }

  static String row(String page, String name) {
    int start = page.lastIndexOf("<tr", page.indexOf("id=\"backup-" + name + "\""));
    assertThat(start).as("the row of " + name).isNotNegative();
    return page.substring(start, page.indexOf("</tr>", start));
  }
}
