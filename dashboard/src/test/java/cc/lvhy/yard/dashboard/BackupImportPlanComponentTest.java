package cc.lvhy.yard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/** An import as a whole: its dry run, the Backup of another Workstation, and the round trip. */
class BackupImportPlanComponentTest extends BackupComponentTest {

  private static Map<String, Object> withoutExportedFrom(String backup) {
    Map<String, Object> file = new LinkedHashMap<>(yaml(backup));
    file.remove("exported-from");
    return file;
  }

  @ParameterizedTest
  @CsvSource({"add,false", "update,false", "add,true", "update,true"})
  void versionThreeRestoresClientCertificateProfiles(String mode, boolean existing) {
    String certificate = newServiceName("certificate");
    if (existing) {
      putRouteProfiles(Map.of(certificate, Map.of("compress", Map.of())));
    }
    String backup =
        """
        version: 3
        route-profiles:
          %s:
            clientCertificate: {}
        """
            .formatted(certificate);
    String path = BACKUP_PATH + "?mode=" + mode;
    ResponseEntity<JsonNode> plan = postYaml(path + "&dry-run=true", backup);

    assertThat(plan.getStatusCode()).isEqualTo(HttpStatus.OK);
    ResponseEntity<JsonNode> before = get(ROUTE_PROFILES_PATH + "/" + certificate);
    if (existing) {
      assertThat(before.getBody().get("type").asString()).isEqualTo("compress");
    } else {
      assertThat(before.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    ResponseEntity<JsonNode> response = postYaml(path, backup);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(entries(response)).isEqualTo(entries(plan));
    assertThat(get(ROUTE_PROFILES_PATH + "/" + certificate).getBody().get("type").asString())
        .isEqualTo(existing && mode.equals("add") ? "compress" : "clientCertificate");
  }

  @Test
  void dryRunShowsThePlanAndChangesNothing() {
    String grafana = newServiceName("grafana");

    ResponseEntity<JsonNode> response =
        postYaml(
            IMPORT_PATH + "&dry-run=true",
            """
            version: 2
            exported-from:
              workstation: ws-other
              at: 2026-11-05T10:00:00Z
            services:
              %s:
                sources:
                  - static-instance:
                      host: 10.1.2.3
                      port: 3000
            """
                .formatted(grafana));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().get("dryRun").asBoolean()).isTrue();
    assertThat(response.getBody().get("mode").asString()).isEqualTo("add");
    assertThat(response.getBody().get("exportedFrom").get("workstation").asString())
        .isEqualTo("ws-other");
    assertThat(entries(response))
        .containsExactly(
            "add service " + grafana, "add static-instance 10.1.2.3:3000 of " + grafana);
    assertThat(get(servicePath(grafana)).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
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
            Map.of("team", "core")));
    put(
        publicationPath(grafana),
        Map.of("scope", "yard", "priority", List.of("static"), "profiles", List.of(compress)));
    String web = newServiceName("web");
    bindCompose(web, "shop", web, 8080);
    String webStatic = register(web, Map.of("host", "10.1.2.3", "port", 8080)).get("id").asString();
    put(instancePriorityPath(web), Map.of("order", List.of(webStatic), "on", false));
    String notes = newServiceName("notes");
    create(notes);
    String docs = newServiceName("docs");
    String docsId = register(docs, Map.of("host", "10.1.2.3", "port", 80)).get("id").asString();
    publish(docs);
    delete(instancesPath(docs) + "/" + docsId);
    String remoteGrafana = newServiceName("grafana");
    remote.register(remoteGrafana, Map.of("host", "10.9.8.7", "port", 3000));
    remote.publish(remoteGrafana, "yard");
    String dash = newServiceName("dash");
    create(dash);
    post(
        AliasesComponentTest.aliasesPath(dash),
        Map.of("workstation", RemoteWorkstation.NAME, "service", remoteGrafana));
    Set<String> names = Set.of(compress, grafana, web, notes, docs, dash);
    String before = only(getYaml(BACKUP_PATH).getBody(), names);
    assertThat(map(map(yaml(before).get("services")).get(web)))
        .containsEntry("instance-priority", false);
    delete(servicePath(grafana));
    delete(servicePath(web));
    delete(servicePath(notes));
    delete(servicePath(docs));
    delete(servicePath(dash));
    delete(ROUTE_PROFILES_PATH + "/" + compress);
    assertThat(yaml(only(getYaml(BACKUP_PATH).getBody(), names)))
        .containsEntry("services", Map.of())
        .containsEntry("route-profiles", Map.of());

    ResponseEntity<JsonNode> response = importBackup(before);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    String after = only(getYaml(BACKUP_PATH).getBody(), names);
    assertThat(withoutExportedFrom(after)).isEqualTo(withoutExportedFrom(before));
  }
}
