package cc.lvhy.yard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** The upload of a Backup file on the Backups page. */
class BackupsUploadComponentTest extends BackupFilesComponentTest {

  @Test
  void uploadLinkOpensTheUploadFormOnItsOwnPage() {
    assertFormPage("/backups", "/backups/upload", "Upload a Backup", "/backups/upload");
    String form = formTo(getPage("/backups/upload").getBody(), "/backups/upload");
    assertThat(openingTag(form, "action=\"/backups/upload\""))
        .contains("enctype=\"multipart/form-data\"");
    assertThat(openingTag(form, "name=\"file\"")).contains("type=\"file\"");
  }

  @Test
  void uploadKeepsTheFileName() throws IOException {
    String name = newFile(".yaml");
    String text = "version: 2\n# from a teammate\n";

    ResponseEntity<String> response = postFile("/backups/upload", "file", name, text);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER);
    assertThat(response.getHeaders().getLocation()).hasPath("/backups");
    assertThat(Files.readString(BACKUPS.resolve(name))).isEqualTo(text);
    assertThat(getPage("/backups").getBody()).contains("id=\"backup-" + name + "\"");
  }

  @ParameterizedTest
  @ValueSource(strings = {"../page-up.yaml", "sub/page-in.yaml", "sub\\page-in.yaml"})
  void uploadRefusesANameWithAPath(String name) throws IOException {
    Set<String> before = names();

    ResponseEntity<String> response = postFile("/backups/upload", "file", name, "version: 2\n");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(alert(response.getBody())).contains("Not uploaded").contains("has a folder in it");
    assertThat(names()).isEqualTo(before);
  }

  @Test
  void uploadRefusesANameThatStartsWithADotAndShowsTheFormAgain() throws IOException {
    Set<String> before = names();

    ResponseEntity<String> response =
        postFile("/backups/upload", "file", ".page-hidden.yaml", "version: 2\n");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(alert(response.getBody())).contains(".page-hidden.yaml starts with a dot");
    assertThat(response.getBody()).contains("<h1>Upload a Backup</h1>");
    assertThat(formTo(response.getBody(), "/backups/upload")).contains("name=\"file\"");
    assertThat(names()).isEqualTo(before);
  }

  @Test
  void uploadRefusesAFileThatIsNotYaml() throws IOException {
    String name = newFile(".txt");

    ResponseEntity<String> response = postFile("/backups/upload", "file", name, "version: 2\n");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(alert(response.getBody())).contains(".yaml or .yml");
    assertThat(BACKUPS.resolve(name)).doesNotExist();
  }

  @Test
  void uploadRefusesANameThatExists() throws IOException {
    String name = newFile(".yaml");
    write(name, "version: 2\n# the first\n");

    ResponseEntity<String> response =
        postFile("/backups/upload", "file", name, "version: 2\n# the second\n");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(alert(response.getBody())).contains(name).contains("already");
    assertThat(Files.readString(BACKUPS.resolve(name))).contains("the first");
  }

  @Test
  void uploadRefusesAFileThatIsNotABackup() {
    String name = newFile(".yaml");

    ResponseEntity<String> response = postFile("/backups/upload", "file", name, "services: [\n");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(alert(response.getBody())).contains("The file is not valid YAML");
    assertThat(BACKUPS.resolve(name)).doesNotExist();
  }
}
