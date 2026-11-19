package cc.lvhy.yard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** The import of a Backup file from the Backups page: the mode, the plan and the confirmation. */
class BackupsImportPageComponentTest extends BackupFilesComponentTest {

  @Test
  void onlyABackupHasAnImportButton() throws IOException {
    String good = newFile(".yaml");
    write(good, "version: 2\n");
    String broken = newFile(".yaml");
    write(broken, "services: [\n");

    String page = getPage("/backups").getBody();

    assertThat(row(page, good)).contains("href=\"/backups/" + good + "/import\"");
    assertThat(row(page, broken)).doesNotContain("/import").contains("The file is not valid YAML");
  }

  @Test
  void importAsksForTheModeFirst() throws IOException {
    String grafana = newServiceName("grafana");
    String name = newFile(".yaml");
    write(name, oneInstance(grafana, 3000));

    ResponseEntity<String> response = getPage("/backups/" + name + "/import");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    String modeForm = form(response.getBody(), "method=\"get\"");
    assertThat(modeForm)
        .contains("action=\"/backups/" + name + "/import\"")
        .contains("name=\"mode\" value=\"add\"")
        .contains("name=\"mode\" value=\"update\"");
    assertThat(response.getBody()).doesNotContain("method=\"post\"");
    assertThat(get(servicePath(grafana)).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void aBackupInAYmlFileCanBeImported() throws IOException {
    String grafana = newServiceName("grafana");
    String name = newFile(".yml");
    write(name, oneInstance(grafana, 3000));

    ResponseEntity<String> response = getPage("/backups/" + name + "/import?mode=add");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(plan(response.getBody()))
        .containsExactly(
            "Add Service " + grafana, "Add Static Instance 10.1.2.3:3000 of " + grafana);
    assertThat(form(response.getBody(), "method=\"post\""))
        .contains("action=\"/backups/" + name + "/import\"");
  }

  @Test
  void modeShowsTheDryRunPlanAndAConfirmButton() throws IOException {
    String grafana = newServiceName("grafana");
    register(grafana, Map.of("host", "10.1.2.3", "port", 3000));
    String name = newFile(".yaml");
    write(name, twoInstances(grafana));

    ResponseEntity<String> response = getPage("/backups/" + name + "/import?mode=add");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(plan(response.getBody()))
        .containsExactlyInAnyOrder(
            "Skip Service " + grafana + ": It exists already.",
            "Skip Static Instance 10.1.2.3:3000 of " + grafana + ": It is the same already.",
            "Add Static Instance 10.1.2.3:3001 of " + grafana,
            "Skip Instance Priority of " + grafana + ": It exists already.");
    String confirm = form(response.getBody(), "method=\"post\"");
    assertThat(confirm)
        .contains("action=\"/backups/" + name + "/import\"")
        .contains("name=\"mode\" value=\"add\"");
    assertThat(get(servicePath(grafana)).getBody().get("instances")).hasSize(1);
  }

  @Test
  void confirmedImportShowsTheResultAndTheSafetyBackup() throws IOException {
    String grafana = newServiceName("grafana");
    String name = newFile(".yaml");
    write(name, oneInstance(grafana, 3000));
    Set<String> before = names();

    ResponseEntity<String> response = confirm(name, "add");

    Set<String> added = names();
    added.removeAll(before);
    added.forEach(file -> files.add(BACKUPS.resolve(file)));
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(plan(response.getBody()))
        .containsExactly(
            "Add Service " + grafana, "Add Static Instance 10.1.2.3:3000 of " + grafana);
    assertThat(added)
        .singleElement()
        .asString()
        .matches(WORKSTATION + "-\\d{8}-\\d{6}-before-import(-\\d+)?\\.yaml");
    assertThat(response.getBody()).contains(added.iterator().next());
    assertThat(get(servicePath(grafana)).getBody().get("instances")).hasSize(1);
  }

  @Test
  void importThatChangesNothingWritesNoSafetyBackup() throws IOException {
    String grafana = newServiceName("grafana");
    register(grafana, Map.of("host", "10.1.2.3", "port", 3000));
    String name = newFile(".yaml");
    write(name, oneInstance(grafana, 3000));
    Set<String> before = names();

    ResponseEntity<String> response = confirm(name, "add");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(names()).isEqualTo(before);
  }

  @Test
  void updateModeOverwritesWhatExists() throws IOException {
    String grafana = newServiceName("grafana");
    register(grafana, Map.of("host", "10.1.2.3", "port", 3000));
    put(publicationPath(grafana), Map.of("scope", "local"));
    String name = newFile(".yaml");
    write(
        name,
        """
        version: 2
        services:
          %s:
            publication:
              scope: yard
        """
            .formatted(grafana));
    Set<String> before = names();

    String preview = getPage("/backups/" + name + "/import?mode=update").getBody();
    ResponseEntity<String> response =
        postForm("/backups/" + name + "/import", fields(form(preview, "method=\"post\"")));

    Set<String> added = names();
    added.removeAll(before);
    added.forEach(file -> files.add(BACKUPS.resolve(file)));
    assertThat(plan(preview)).contains("Update Publication of " + grafana);
    assertThat(form(preview, "method=\"post\"")).contains("name=\"mode\" value=\"update\"");
    assertThat(plan(response.getBody())).contains("Update Publication of " + grafana);
    assertThat(get(servicePath(grafana)).getBody().get("publication").get("scope").asString())
        .isEqualTo("yard");
  }

  @Test
  void confirmShowsTheNewPlanAndChangesNothingWhenTheSetupChanged() throws IOException {
    String grafana = newServiceName("grafana");
    String name = newFile(".yaml");
    write(name, twoInstances(grafana));
    String preview = getPage("/backups/" + name + "/import?mode=add").getBody();
    register(grafana, Map.of("host", "10.1.2.3", "port", 3000));
    Set<String> before = names();

    ResponseEntity<String> response =
        postForm("/backups/" + name + "/import", fields(form(preview, "method=\"post\"")));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(alert(response.getBody())).contains("The plan changed");
    assertThat(plan(response.getBody()))
        .containsExactlyInAnyOrder(
            "Skip Service " + grafana + ": It exists already.",
            "Skip Static Instance 10.1.2.3:3000 of " + grafana + ": It is the same already.",
            "Add Static Instance 10.1.2.3:3001 of " + grafana,
            "Skip Instance Priority of " + grafana + ": It exists already.");
    assertThat(names()).isEqualTo(before);
    assertThat(get(servicePath(grafana)).getBody().get("instances")).hasSize(1);

    ResponseEntity<String> again =
        postForm(
            "/backups/" + name + "/import", fields(form(response.getBody(), "method=\"post\"")));

    Set<String> added = names();
    added.removeAll(before);
    added.forEach(file -> files.add(BACKUPS.resolve(file)));
    assertThat(again.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(get(servicePath(grafana)).getBody().get("instances")).hasSize(2);
  }

  @Test
  void confirmShowsTheNewPlanWhenTheFileChanged() throws IOException {
    String grafana = newServiceName("grafana");
    String name = newFile(".yaml");
    write(name, oneInstance(grafana, 3000));
    String preview = getPage("/backups/" + name + "/import?mode=add").getBody();
    write(name, twoInstances(grafana));
    Set<String> before = names();

    ResponseEntity<String> response =
        postForm("/backups/" + name + "/import", fields(form(preview, "method=\"post\"")));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(plan(response.getBody()))
        .contains("Add Static Instance 10.1.2.3:3001 of " + grafana);
    assertThat(names()).isEqualTo(before);
    assertThat(get(servicePath(grafana)).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void fileThatDoesNotParseCannotBeImported() throws IOException {
    String name = newFile(".yaml");
    write(name, "services: [\n");
    Set<String> before = names();

    ResponseEntity<String> preview = getPage("/backups/" + name + "/import?mode=add");
    ResponseEntity<String> confirmed =
        postForm("/backups/" + name + "/import", Map.of("mode", "add", "plan", "unknown"));

    assertThat(preview.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(alert(preview.getBody())).contains("The file is not valid YAML");
    assertThat(preview.getBody()).doesNotContain("method=\"post\"");
    assertThat(confirmed.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(alert(confirmed.getBody())).contains("The file is not valid YAML");
    assertThat(names()).isEqualTo(before);
  }

  @Test
  void refusedPlanShowsEachProblemAndNoConfirmButton() throws IOException {
    String grafana = newServiceName("grafana");
    String name = newFile(".yaml");
    write(name, oneInstance(grafana, 70000));

    ResponseEntity<String> response = getPage("/backups/" + name + "/import?mode=add");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(alert(response.getBody()))
        .contains("services." + grafana + ".sources[0].static-instance.port: ");
    assertThat(response.getBody()).doesNotContain("method=\"post\"");
  }

  @Test
  void unknownModeIsRefused() throws IOException {
    String name = newFile(".yaml");
    write(name, "version: 2\n");

    assertThat(getPage("/backups/" + name + "/import?mode=merge").getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(
            postForm("/backups/" + name + "/import", Map.of("mode", "merge", "plan", "unknown"))
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void importOfAMissingBackupIsNotFound() {
    assertThat(getPage("/backups/page-missing.yaml/import").getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(
            postForm("/backups/page-missing.yaml/import", Map.of("mode", "add", "plan", "unknown"))
                .getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  private static String oneInstance(String service, int port) {
    return """
    version: 2
    services:
      %s:
        sources:
          - static-instance:
              host: 10.1.2.3
              port: %d
    """
        .formatted(service, port);
  }

  private static String twoInstances(String service) {
    return """
    version: 2
    services:
      %s:
        sources:
          - static-instance:
              host: 10.1.2.3
              port: 3000
          - static-instance:
              host: 10.1.2.3
              port: 3001
    """
        .formatted(service);
  }

  /** Each entry of the plan, as its text. */
  private static List<String> plan(String page) {
    int start = page.indexOf("id=\"plan\"");
    assertThat(start).as("the plan").isNotNegative();
    String list = div(page, "id=\"plan\"");
    return Pattern.compile("<li[^>]*>(.*?)</li>", Pattern.DOTALL)
        .matcher(list)
        .results()
        .map(match -> match.group(1).replaceAll("<[^>]+>", "").replaceAll("\\s+", " ").trim())
        .toList();
  }

  /** Shows the plan in {@code mode}, then confirms it as a person does. */
  private ResponseEntity<String> confirm(String name, String mode) {
    String preview = getPage("/backups/" + name + "/import?mode=" + mode).getBody();
    return postForm("/backups/" + name + "/import", fields(form(preview, "method=\"post\"")));
  }

  /** The hidden fields of {@code form}, which a browser posts. */
  private static Map<String, String> fields(String form) {
    return Pattern.compile(
            "<input[^>]*type=\"hidden\"[^>]*name=\"([^\"]*)\"[^>]*value=\"([^\"]*)\"")
        .matcher(form)
        .results()
        .collect(Collectors.toMap(match -> match.group(1), match -> match.group(2)));
  }

  /** The whole {@code form} element with {@code attribute}. */
  private static String form(String page, String attribute) {
    int at = page.indexOf(attribute);
    assertThat(at).as("a form with " + attribute).isNotNegative();
    int start = page.lastIndexOf("<form", at);
    return page.substring(start, page.indexOf("</form>", start));
  }
}
