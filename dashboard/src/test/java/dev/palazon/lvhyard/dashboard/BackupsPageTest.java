package dev.palazon.lvhyard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** The Backups page, where the files of the Backups folder are seen, made and deleted. */
class BackupsPageTest extends DashboardTest {

  /** Every test class of this JVM shares the Backups folder, so each test names its own files. */
  private final List<Path> files = new ArrayList<>();

  @AfterEach
  void deleteTheFiles() throws IOException {
    for (Path file : files) {
      Files.deleteIfExists(file);
    }
  }

  @Test
  void listsEachBackupWithItsDateAndCounts() throws IOException {
    String name = newFile(".yaml");
    write(
        name,
        """
        version: 1
        exported-from:
          workstation: ws-other
          at: "2026-11-05T10:15:00+01:00"
        route-profiles:
          office:
            ipAllowList:
              sourceRange: [192.168.1.0/24]
        services:
          grafana: {}
          loki: {}
        aliases:
          logs: {workstation: ws-other, service: loki}
        """);

    String row = row(getPage("/backups").getBody(), name);

    assertThat(row)
        .contains("2026-11-05 10:15")
        .contains("2 Services")
        .contains("1 Alias")
        .contains("1 Route Profile");
  }

  @Test
  void listsOnlyYamlFiles() throws IOException {
    String yml = newFile(".yml");
    write(yml, "version: 1\n");
    String text = newFile(".txt");
    write(text, "version: 1\n");

    String page = getPage("/backups").getBody();

    assertThat(page).contains(yml).doesNotContain(text);
  }

  @Test
  void fileThatIsNotABackupShowsItsError() throws IOException {
    String name = newFile(".yaml");
    write(name, "services: [\n");

    assertThat(row(getPage("/backups").getBody(), name)).contains("The file is not valid YAML");
  }

  @Test
  void createButtonWritesABackupNamedAfterTheWorkstationAndTheTime() throws IOException {
    String old = newFile(".yaml");
    write(old, "version: 1\n");
    String web = newServiceName("web");
    register(web, Map.of("host", "10.1.2.3", "port", 8080));
    assertThat(getPage("/backups").getBody()).contains("action=\"/backups/new\"");
    Set<String> before = names();

    ResponseEntity<String> response = postForm("/backups/new", Map.of());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER);
    assertThat(response.getHeaders().getLocation()).hasPath("/backups");
    Set<String> added = names();
    added.removeAll(before);
    assertThat(added)
        .singleElement()
        .asString()
        .matches(WORKSTATION + "-\\d{8}-\\d{6}(-\\d+)?\\.yaml");
    String name = added.iterator().next();
    files.add(TestcontainersConfiguration.BACKUPS.resolve(name));
    assertThat(Files.readString(TestcontainersConfiguration.BACKUPS.resolve(name))).contains(web);
    assertThat(getPage("/backups").getBody()).contains(name).contains(old);
  }

  @Test
  void downloadGivesTheFileAsItIs() throws IOException {
    String name = newFile(".yaml");
    String text = "version: 1\n# kept as it is\n";
    write(name, text);
    assertThat(row(getPage("/backups").getBody(), name))
        .contains("href=\"/backups/" + name + "/download\"");

    ResponseEntity<String> response = getPage("/backups/" + name + "/download");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getContentDisposition().isAttachment()).isTrue();
    assertThat(response.getHeaders().getContentDisposition().getFilename()).isEqualTo(name);
    assertThat(response.getBody()).isEqualTo(text);
  }

  @Test
  void downloadOfAMissingBackupIsNotFound() {
    assertThat(getPage("/backups/page-missing.yaml/download").getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void deleteAsksForAConfirmationFirst() throws IOException {
    String name = newFile(".yaml");
    write(name, "version: 1\n");
    String deletePath = "/backups/" + name + "/delete";
    assertThat(row(getPage("/backups").getBody(), name)).contains("href=\"" + deletePath + "\"");

    ResponseEntity<String> confirm = getPage(deletePath);

    assertThat(confirm.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(confirm.getBody()).contains(name).contains("action=\"" + deletePath + "\"");
    assertThat(TestcontainersConfiguration.BACKUPS.resolve(name)).exists();
  }

  @Test
  void confirmedDeleteRemovesTheFile() throws IOException {
    String name = newFile(".yaml");
    write(name, "version: 1\n");

    ResponseEntity<String> response = postForm("/backups/" + name + "/delete", Map.of());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER);
    assertThat(response.getHeaders().getLocation()).hasPath("/backups");
    assertThat(TestcontainersConfiguration.BACKUPS.resolve(name)).doesNotExist();
    assertThat(getPage("/backups").getBody()).doesNotContain(name);
  }

  @Test
  void deleteOfAMissingBackupIsNotFound() {
    assertThat(postForm("/backups/page-missing.yaml/delete", Map.of()).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  private static Set<String> names() throws IOException {
    try (Stream<Path> files = Files.list(TestcontainersConfiguration.BACKUPS)) {
      return files
          .map(file -> file.getFileName().toString())
          .collect(Collectors.toCollection(HashSet::new));
    }
  }

  private String newFile(String extension) {
    String name = "page-" + UUID.randomUUID() + extension;
    files.add(TestcontainersConfiguration.BACKUPS.resolve(name));
    return name;
  }

  private static void write(String name, String text) throws IOException {
    Files.createDirectories(TestcontainersConfiguration.BACKUPS);
    Files.writeString(TestcontainersConfiguration.BACKUPS.resolve(name), text);
  }

  private static String row(String page, String name) {
    int start = page.lastIndexOf("<tr", page.indexOf("id=\"backup-" + name + "\""));
    assertThat(start).as("the row of " + name).isNotNegative();
    return page.substring(start, page.indexOf("</tr>", start));
  }
}
