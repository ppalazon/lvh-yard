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
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** The Backups page, where the files of the Backups folder are seen, made and deleted. */
class BackupsPageIT extends DashboardIT {

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

  @Test
  void uploadLinkOpensTheUploadFormOnItsOwnPage() {
    assertFormPage("/backups", "/backups/upload", "Upload a Backup", "/backups/upload");
    String form = formTo(getPage("/backups/upload").getBody(), "/backups/upload");
    assertThat(openingTag(form, "action=\"/backups/upload\""))
        .contains("enctype=\"multipart/form-data\"");
    assertThat(openingTag(form, "name=\"file\"")).contains("type=\"file\"");
  }

  @Test
  void uploadFormIsShownAgainWithWhyTheUploadWasRefused() {
    ResponseEntity<String> response =
        postFile("/backups/upload", "file", ".page-hidden.yaml", "version: 1\n");

    assertThat(response.getBody()).contains("<h1>Upload a Backup</h1>");
    String form = formTo(response.getBody(), "/backups/upload");
    assertThat(form.indexOf("name=\"file\""))
        .isLessThan(form.indexOf(".page-hidden.yaml starts with a dot"));
  }

  @Test
  void uploadKeepsTheFileName() throws IOException {
    String name = newFile(".yaml");
    String text = "version: 1\n# from a teammate\n";

    ResponseEntity<String> response = postFile("/backups/upload", "file", name, text);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER);
    assertThat(response.getHeaders().getLocation()).hasPath("/backups");
    assertThat(Files.readString(TestcontainersConfiguration.BACKUPS.resolve(name))).isEqualTo(text);
    assertThat(getPage("/backups").getBody()).contains("id=\"backup-" + name + "\"");
  }

  @ParameterizedTest
  @ValueSource(strings = {"../page-up.yaml", "sub/page-in.yaml", "sub\\page-in.yaml"})
  void uploadRefusesANameWithAPath(String name) throws IOException {
    Set<String> before = names();

    ResponseEntity<String> response = postFile("/backups/upload", "file", name, "version: 1\n");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(alert(response.getBody())).contains("Not uploaded").contains("has a folder in it");
    assertThat(names()).isEqualTo(before);
    assertThat(TestcontainersConfiguration.BACKUPS.resolveSibling("page-up.yaml")).doesNotExist();
  }

  @Test
  void uploadRefusesANameThatStartsWithADot() throws IOException {
    Set<String> before = names();

    ResponseEntity<String> response =
        postFile("/backups/upload", "file", ".page-hidden.yaml", "version: 1\n");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(alert(response.getBody())).contains(".page-hidden.yaml starts with a dot");
    assertThat(names()).isEqualTo(before);
  }

  @Test
  void uploadRefusesAFileThatIsNotYaml() throws IOException {
    String name = newFile(".txt");

    ResponseEntity<String> response = postFile("/backups/upload", "file", name, "version: 1\n");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(alert(response.getBody())).contains(".yaml or .yml");
    assertThat(TestcontainersConfiguration.BACKUPS.resolve(name)).doesNotExist();
  }

  @Test
  void uploadRefusesANameThatExists() throws IOException {
    String name = newFile(".yaml");
    write(name, "version: 1\n# the first\n");

    ResponseEntity<String> response =
        postFile("/backups/upload", "file", name, "version: 1\n# the second\n");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(alert(response.getBody())).contains(name).contains("already");
    assertThat(Files.readString(TestcontainersConfiguration.BACKUPS.resolve(name)))
        .contains("the first");
  }

  @Test
  void uploadRefusesAFileThatIsNotABackup() {
    String name = newFile(".yaml");

    ResponseEntity<String> response = postFile("/backups/upload", "file", name, "services: [\n");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(alert(response.getBody())).contains("The file is not valid YAML");
    assertThat(TestcontainersConfiguration.BACKUPS.resolve(name)).doesNotExist();
  }

  @Test
  void onlyABackupHasAnImportButton() throws IOException {
    String good = newFile(".yaml");
    write(good, "version: 1\n");
    String broken = newFile(".yaml");
    write(broken, "services: [\n");

    String page = getPage("/backups").getBody();

    assertThat(row(page, good)).contains("href=\"/backups/" + good + "/import\"");
    assertThat(row(page, broken)).doesNotContain("/import");
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
  void modeShowsTheDryRunPlanAndAConfirmButton() throws IOException {
    String grafana = newServiceName("grafana");
    register(grafana, Map.of("host", "10.1.2.3", "port", 3000));
    String name = newFile(".yaml");
    write(name, twoInstances(grafana));

    ResponseEntity<String> response = getPage("/backups/" + name + "/import?mode=add");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(plan(response.getBody()))
        .containsExactly(
            "Skip Service " + grafana + ": It exists already.",
            "Skip Static Instance 10.1.2.3:3000 of " + grafana + ": It is the same already.",
            "Add Static Instance 10.1.2.3:3001 of " + grafana);
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
    added.forEach(file -> files.add(TestcontainersConfiguration.BACKUPS.resolve(file)));
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
    assertThat(response.getBody()).contains("Nothing changed");
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
        version: 1
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
    added.forEach(file -> files.add(TestcontainersConfiguration.BACKUPS.resolve(file)));
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
        .containsExactly(
            "Skip Service " + grafana + ": It exists already.",
            "Skip Static Instance 10.1.2.3:3000 of " + grafana + ": It is the same already.",
            "Add Static Instance 10.1.2.3:3001 of " + grafana);
    assertThat(names()).isEqualTo(before);
    assertThat(get(servicePath(grafana)).getBody().get("instances")).hasSize(1);

    ResponseEntity<String> again =
        postForm(
            "/backups/" + name + "/import", fields(form(response.getBody(), "method=\"post\"")));

    Set<String> added = names();
    added.removeAll(before);
    added.forEach(file -> files.add(TestcontainersConfiguration.BACKUPS.resolve(file)));
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
        .contains("services." + grafana + ".static-instances[0].port: ");
    assertThat(response.getBody()).doesNotContain("method=\"post\"");
  }

  @Test
  void unknownModeIsRefused() throws IOException {
    String name = newFile(".yaml");
    write(name, "version: 1\n");

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
    version: 1
    services:
      %s:
        static-instances:
          - host: 10.1.2.3
            port: %d
    """
        .formatted(service, port);
  }

  private static String twoInstances(String service) {
    return """
    version: 1
    services:
      %s:
        static-instances:
          - host: 10.1.2.3
            port: 3000
          - host: 10.1.2.3
            port: 3001
    """
        .formatted(service);
  }

  /** Each entry of the plan, as its text. */
  private static List<String> plan(String page) {
    int start = page.indexOf("id=\"plan\"");
    assertThat(start).as("the plan").isNotNegative();
    String list = page.substring(start, page.indexOf("</ul>", start));
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

  private static String alert(String page) {
    return div(page, "role=\"alert\"");
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
