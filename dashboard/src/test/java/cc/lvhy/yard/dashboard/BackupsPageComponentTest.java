package cc.lvhy.yard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** The Backups page: files of the Backups folder are seen, made, downloaded and deleted. */
class BackupsPageComponentTest extends BackupFilesComponentTest {

  @Test
  void listsEachBackupWithItsDateAndCounts() throws IOException {
    String name = newFile(".yaml");
    write(
        name,
        """
        version: 2
        exported-from:
          workstation: ws-other
          at: "2026-11-05T10:15:00+01:00"
        route-profiles:
          office:
            ipAllowList:
              sourceRange: [192.168.1.0/24]
        services:
          grafana:
            sources:
              - alias:
                  {workstation: ws-other, service: grafana}
          loki: {}
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
    write(yml, "version: 2\n");
    String text = newFile(".txt");
    write(text, "version: 2\n");

    String page = getPage("/backups").getBody();

    assertThat(page).contains(yml).doesNotContain(text);
  }

  @Test
  void createButtonWritesABackupNamedAfterTheWorkstationAndTheTime() throws IOException {
    String old = newFile(".yaml");
    write(old, "version: 2\n");
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
    files.add(BACKUPS.resolve(name));
    assertThat(Files.readString(BACKUPS.resolve(name))).contains(web);
    assertThat(getPage("/backups").getBody()).contains(name).contains(old);
  }

  @Test
  void downloadGivesTheFileAsItIs() throws IOException {
    String name = newFile(".yaml");
    String text = "version: 2\n# kept as it is\n";
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
    write(name, "version: 2\n");
    String deletePath = "/backups/" + name + "/delete";
    assertThat(row(getPage("/backups").getBody(), name)).contains("href=\"" + deletePath + "\"");

    ResponseEntity<String> confirm = getPage(deletePath);

    assertThat(confirm.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(confirm.getBody()).contains(name).contains("action=\"" + deletePath + "\"");
    assertThat(BACKUPS.resolve(name)).exists();
  }

  @Test
  void confirmedDeleteRemovesTheFile() throws IOException {
    String name = newFile(".yaml");
    write(name, "version: 2\n");

    ResponseEntity<String> response = postForm("/backups/" + name + "/delete", Map.of());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER);
    assertThat(response.getHeaders().getLocation()).hasPath("/backups");
    assertThat(BACKUPS.resolve(name)).doesNotExist();
    assertThat(getPage("/backups").getBody()).doesNotContain(name);
  }

  @Test
  void deleteOfAMissingBackupIsNotFound() {
    assertThat(postForm("/backups/page-missing.yaml/delete", Map.of()).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }
}
