package dev.palazon.lvhyard.dashboard;

import static dev.palazon.lvhyard.dashboard.BackupExportIT.map;
import static dev.palazon.lvhyard.dashboard.BackupExportIT.yaml;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.yaml.snakeyaml.Yaml;
import tools.jackson.databind.JsonNode;

/** An import in add mode: it adds what is new and skips what exists. */
class BackupImportIT extends DashboardIT {

  static final String IMPORT_PATH = BackupExportIT.BACKUP_PATH + "?mode=add";

  ResponseEntity<JsonNode> importBackup(String backup) {
    return postYaml(IMPORT_PATH, backup);
  }

  /** The Backup with only the entries named {@code names}, as other tests share this Consul. */
  static String only(String backup, Set<String> names) {
    Map<String, Object> file = new LinkedHashMap<>(yaml(backup));
    for (String key : List.of("route-profiles", "services", "aliases")) {
      Map<String, Object> kept = new LinkedHashMap<>(map(file.get(key)));
      kept.keySet().retainAll(names);
      file.put(key, kept);
    }
    return new Yaml().dump(file);
  }

  private static Map<String, Object> withoutExportedFrom(String backup) {
    Map<String, Object> file = new LinkedHashMap<>(yaml(backup));
    file.remove("exported-from");
    return file;
  }

  /** The place of each problem of a refusal. */
  static List<String> problems(ResponseEntity<JsonNode> response) {
    return response
        .getBody()
        .get("problems")
        .valueStream()
        .map(problem -> problem.get("place").asString())
        .toList();
  }

  /** Each plan entry as one line, such as {@code skip alias dash: It exists already.}. */
  static List<String> entries(ResponseEntity<JsonNode> response) {
    List<String> lines = new ArrayList<>();
    for (JsonNode entry : response.getBody().get("entries")) {
      StringBuilder line =
          new StringBuilder(entry.get("action").asString())
              .append(' ')
              .append(entry.get("kind").asString())
              .append(' ')
              .append(entry.get("name").asString());
      if (entry.hasNonNull("service")) {
        line.append(" of ").append(entry.get("service").asString());
      }
      if (entry.hasNonNull("reason")) {
        line.append(": ").append(entry.get("reason").asString());
      }
      lines.add(line.toString());
    }
    return lines;
  }

  @Test
  void aliasIsAddedAndAnExistingOneIsSkipped() {
    String grafana = newServiceName("grafana");
    remote.register(grafana, Map.of("host", "10.9.8.7", "port", 3000));
    remote.publish(grafana, "yard");
    String dash = newServiceName("dash");
    String board = newServiceName("board");
    put(
        "/api/workstation/aliases/" + dash,
        Map.of("workstation", RemoteWorkstation.NAME, "service", grafana));

    ResponseEntity<JsonNode> response =
        importBackup(
            """
            version: 1
            aliases:
              %s:
                workstation: %s
                service: %s
              %s:
                workstation: %s
                service: %s
            """
                .formatted(
                    dash, RemoteWorkstation.NAME, grafana, board, RemoteWorkstation.NAME, grafana));

    assertThat(entries(response))
        .containsExactly("skip alias " + dash + ": It is the same already.", "add alias " + board);
    assertThat(get("/api/workstation/aliases").getBody().findValuesAsString("name"))
        .contains(dash, board);
  }

  @Test
  void backupFromTheRemoteWorkstationAddsItsServicesHere() {
    String grafana = newServiceName("grafana");
    remote.register(grafana, Map.of("port", 3000));
    remote.publish(grafana, "yard");

    ResponseEntity<JsonNode> response = importBackup(only(remote.backup(), Set.of(grafana)));

    assertThat(response.getBody().get("exportedFrom").get("workstation").asString())
        .isEqualTo(RemoteWorkstation.NAME);
    assertThat(entries(response))
        .containsExactly(
            "add service " + grafana,
            "add static-instance " + lanAddress() + ":3000 of " + grafana,
            "add publication " + grafana);
    JsonNode service = get(servicePath(grafana)).getBody();
    assertThat(service.get("workstation").asString()).isEqualTo(WORKSTATION);
    assertThat(service.get("instances").get(0).get("host").asString()).isEqualTo(lanAddress());
    assertThat(service.get("publication").get("scope").asString()).isEqualTo("yard");
  }

  @Test
  void roundTripGivesTheSameBackup() {
    String compress = newServiceName("compress");
    putRouteProfiles(Map.of(compress, Map.of("compress", Map.of())));
    String grafana = newServiceName("grafana");
    String grafanaId =
        register(
                grafana,
                Map.of(
                    "port",
                    3000,
                    "check",
                    Map.of("path", "/health", "interval", "5s"),
                    "tags",
                    List.of("blue"),
                    "meta",
                    Map.of("team", "core")))
            .get("id")
            .asString();
    put(
        publicationPath(grafana),
        Map.of("scope", "yard", "priority", List.of("static"), "profiles", List.of(compress)));
    String web = newServiceName("web");
    String webBinding = bindCompose(web, "shop", web, 8080).get("id").asString();
    String docs = newServiceName("docs");
    String docsId = register(docs, Map.of("host", "10.1.2.3", "port", 80)).get("id").asString();
    publish(docs);
    delete(instancesPath(docs) + "/" + docsId);
    String remoteGrafana = newServiceName("grafana");
    remote.register(remoteGrafana, Map.of("host", "10.9.8.7", "port", 3000));
    remote.publish(remoteGrafana, "yard");
    String dash = newServiceName("dash");
    put(
        "/api/workstation/aliases/" + dash,
        Map.of("workstation", RemoteWorkstation.NAME, "service", remoteGrafana));
    Set<String> names = Set.of(compress, grafana, web, docs, dash);
    String before = only(getYaml(BackupExportIT.BACKUP_PATH).getBody(), names);
    delete(publicationPath(grafana));
    delete(instancesPath(grafana) + "/" + grafanaId);
    delete(containerBindingsPath(web) + "/" + webBinding);
    delete(publicationPath(docs));
    delete("/api/workstation/aliases/" + dash);
    delete(ROUTE_PROFILES_PATH + "/" + compress);
    assertThat(yaml(only(getYaml(BackupExportIT.BACKUP_PATH).getBody(), names)))
        .containsEntry("services", Map.of())
        .containsEntry("aliases", Map.of())
        .containsEntry("route-profiles", Map.of());

    ResponseEntity<JsonNode> response = importBackup(before);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    String after = only(getYaml(BackupExportIT.BACKUP_PATH).getBody(), names);
    assertThat(withoutExportedFrom(after)).isEqualTo(withoutExportedFrom(before));
  }
}
