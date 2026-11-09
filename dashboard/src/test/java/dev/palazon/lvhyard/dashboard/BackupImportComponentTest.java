package dev.palazon.lvhyard.dashboard;

import static dev.palazon.lvhyard.dashboard.BackupExportComponentTest.map;
import static dev.palazon.lvhyard.dashboard.BackupExportComponentTest.yaml;
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
class BackupImportComponentTest extends DashboardComponentTest {

  static final String IMPORT_PATH = BackupExportComponentTest.BACKUP_PATH + "?mode=add";

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
  void newServiceGetsItsStaticInstanceAndStaysUnpublished() {
    String grafana = newServiceName("grafana");

    ResponseEntity<JsonNode> response =
        importBackup(
            """
            version: 1
            services:
              %s:
                static-instances:
                  - host: 10.1.2.3
                    port: 3000
            """
                .formatted(grafana));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(entries(response))
        .containsExactly(
            "add service " + grafana, "add static-instance 10.1.2.3:3000 of " + grafana);
    JsonNode service = get(servicePath(grafana)).getBody();
    assertThat(service.get("instances").get(0).get("host").asString()).isEqualTo("10.1.2.3");
    assertThat(service.get("instances").get(0).get("port").asInt()).isEqualTo(3000);
    assertThat(service.get("state").asString()).isEqualTo("unpublished");
  }

  @Test
  void existingEntriesAreSkippedWithAReason() {
    String grafana = newServiceName("grafana");
    register(grafana, Map.of("host", "10.1.2.3", "port", 3000));
    register(grafana, Map.of("host", "10.1.2.3", "port", 3001, "tags", List.of("blue")));

    ResponseEntity<JsonNode> response =
        importBackup(
            """
            version: 1
            services:
              %s:
                static-instances:
                  - host: 10.1.2.3
                    port: 3000
                  - host: 10.1.2.3
                    port: 3001
                    tags: [green]
                  - host: 10.1.2.3
                    port: 3002
            """
                .formatted(grafana));

    assertThat(entries(response))
        .containsExactly(
            "skip service " + grafana + ": It exists already.",
            "skip static-instance 10.1.2.3:3000 of " + grafana + ": It is the same already.",
            "skip static-instance 10.1.2.3:3001 of " + grafana + ": It exists already.",
            "add static-instance 10.1.2.3:3002 of " + grafana);
    JsonNode instances = get(servicePath(grafana)).getBody().get("instances");
    assertThat(instances).hasSize(3);
    assertThat(
            instances
                .valueStream()
                .filter(instance -> instance.get("port").asInt() == 3001)
                .findFirst()
                .orElseThrow()
                .get("tags")
                .get(0)
                .asString())
        .isEqualTo("blue");
  }

  @Test
  void staticInstanceWithoutAHostIsAtTheLanAddressAndMatchesThere() {
    String grafana = newServiceName("grafana");
    String loki = newServiceName("loki");
    register(grafana, Map.of("port", 3000));

    ResponseEntity<JsonNode> response =
        importBackup(
            """
            version: 1
            services:
              %s:
                static-instances:
                  - port: 3000
              %s:
                static-instances:
                  - host: %s
                    port: 3000
            """
                .formatted(grafana, loki, lanAddress()));

    String address = lanAddress() + ":3000";
    assertThat(entries(response))
        .containsExactly(
            "skip service " + grafana + ": It exists already.",
            "skip static-instance " + address + " of " + grafana + ": It is the same already.",
            "add service " + loki,
            "add static-instance " + address + " of " + loki);
    assertThat(get(servicePath(grafana)).getBody().get("instances")).hasSize(1);
    assertThat(get(servicePath(loki)).getBody().get("instances").get(0).get("host").asString())
        .isEqualTo(lanAddress());
  }

  @Test
  void containerBindingMatchesByItsId() {
    String web = newServiceName("web");
    bindCompose(web, "shop", web, 8080);

    ResponseEntity<JsonNode> response =
        importBackup(
            """
            version: 1
            services:
              %s:
                container-bindings:
                  - compose: {project: shop, service: %s}
                    port: 8080
                  - container: %s-1
                    port: 80
            """
                .formatted(web, web, web));

    assertThat(entries(response))
        .containsExactly(
            "skip service " + web + ": It exists already.",
            "skip container-binding compose:shop:"
                + web
                + ":8080 of "
                + web
                + ": It is the same already.",
            "add container-binding " + web + "-1:80 of " + web);
    assertThat(
            get(servicePath(web))
                .getBody()
                .get("containerBindings")
                .valueStream()
                .map(binding -> binding.get("id").asString()))
        .containsExactlyInAnyOrder("compose:shop:" + web + ":8080", web + "-1:80");
  }

  @Test
  void publicationCanNameARouteProfileOfTheSameFile() {
    String grafana = newServiceName("grafana");
    String compress = newServiceName("compress");

    ResponseEntity<JsonNode> response =
        importBackup(
            """
            version: 1
            route-profiles:
              %s:
                compress: {}
            services:
              %s:
                publication:
                  scope: yard
                  priority: [bound, static]
                  route-profiles: [%s]
                static-instances:
                  - host: 10.1.2.3
                    port: 3000
            """
                .formatted(compress, grafana, compress));

    assertThat(entries(response))
        .containsExactly(
            "add route-profile " + compress,
            "add service " + grafana,
            "add static-instance 10.1.2.3:3000 of " + grafana,
            "add publication " + grafana);
    JsonNode service = get(servicePath(grafana)).getBody();
    assertThat(service.get("publication").get("scope").asString()).isEqualTo("yard");
    assertThat(service.get("priority").valueStream().map(JsonNode::asString))
        .containsExactly("bound", "static");
    assertThat(service.get("profiles").valueStream().map(JsonNode::asString))
        .containsExactly(compress);
  }

  @Test
  void missingPublicationKeyKeepsTheExistingPublication() {
    String grafana = newServiceName("grafana");
    register(grafana, Map.of("host", "10.1.2.3", "port", 3000));
    put(publicationPath(grafana), Map.of("scope", "yard"));

    importBackup(
        """
        version: 1
        services:
          %s:
            static-instances:
              - host: 10.1.2.3
                port: 3001
        """
            .formatted(grafana));

    assertThat(get(servicePath(grafana)).getBody().get("publication").get("scope").asString())
        .isEqualTo("yard");
  }

  @Test
  void serviceWithOnlyAPublicationIsAdded() {
    String grafana = newServiceName("grafana");

    ResponseEntity<JsonNode> response =
        importBackup(
            """
            version: 1
            services:
              %s:
                publication:
                  scope: local
            """
                .formatted(grafana));

    assertThat(entries(response))
        .containsExactly("add service " + grafana, "add publication " + grafana);
    assertThat(get(servicePath(grafana)).getBody().get("publication").get("scope").asString())
        .isEqualTo("local");
  }

  @Test
  void eachProblemNamesItsPlaceAndNothingChanges() {
    String grafana = newServiceName("grafana");
    String loki = newServiceName("loki");

    ResponseEntity<JsonNode> response =
        importBackup(
            """
            version: 1
            services:
              %s:
                static-instances:
                  - host: 10.1.2.3
                    port: 3000
              %s:
                publication:
                  scope: everywhere
                  route-profiles: [missing-profile]
                static-instances:
                  - host: 10.1.2.3
                    port: 70000
            """
                .formatted(grafana, loki));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(problems(response))
        .containsExactly(
            "services." + loki + ".static-instances[0].port",
            "services." + loki + ".publication.scope",
            "services." + loki + ".publication.route-profiles[0]");
    assertThat(response.getBody().get("problems").get(2).get("reason").asString())
        .contains("missing-profile");
    assertThat(get(servicePath(grafana)).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void unknownKeyIsRefused() {
    String grafana = newServiceName("grafana");

    ResponseEntity<JsonNode> response =
        importBackup(
            """
            version: 1
            services:
              %s:
                static-instance:
                  - port: 3000
            """
                .formatted(grafana));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(problems(response)).containsExactly("services." + grafana + ".static-instance");
    assertThat(get(servicePath(grafana)).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void unknownVersionIsRefused() {
    ResponseEntity<JsonNode> response = importBackup("version: 2\n");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(problems(response)).containsExactly("version");
  }

  @Test
  void refusedRouteProfileIsRefusedWithItsPlace() {
    String grafana = newServiceName("grafana");
    String auth = newServiceName("auth");

    ResponseEntity<JsonNode> response =
        importBackup(
            """
            version: 1
            route-profiles:
              %s:
                basicAuth:
                  usersFile: /etc/users
            services:
              %s:
                publication:
                  route-profiles: [%s]
                static-instances:
                  - host: 10.1.2.3
                    port: 3000
            """
                .formatted(auth, grafana, auth));

    assertThat(problems(response)).containsExactly("route-profiles." + auth);
    assertThat(get(ROUTE_PROFILES_PATH + "/" + auth).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void bindingATargetThatAnotherServiceFollowsIsRefused() {
    String web = newServiceName("web");
    String shop = newServiceName("shop");
    bindCompose(web, "shop", web, 8080);

    ResponseEntity<JsonNode> response =
        importBackup(
            """
            version: 1
            services:
              %s:
                container-bindings:
                  - compose: {project: shop, service: %s}
                    port: 9090
            """
                .formatted(shop, web));

    assertThat(problems(response)).containsExactly("services." + shop + ".container-bindings[0]");
    assertThat(response.getBody().get("problems").get(0).get("reason").asString()).contains(web);
  }

  @Test
  void dryRunShowsThePlanAndChangesNothing() {
    String grafana = newServiceName("grafana");

    ResponseEntity<JsonNode> response =
        postYaml(
            IMPORT_PATH + "&dry-run=true",
            """
            version: 1
            exported-from:
              workstation: ws-other
              at: 2026-11-05T10:00:00Z
            services:
              %s:
                static-instances:
                  - host: 10.1.2.3
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
  void unknownModeIsRefused() {
    ResponseEntity<JsonNode> response =
        postYaml(BackupExportComponentTest.BACKUP_PATH + "?mode=merge", "version: 1\n");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().get("detail").asString()).contains("merge");
  }

  @Test
  void aliasToThisWorkstationIsRefused() {
    String grafana = newServiceName("grafana");
    register(grafana, Map.of("host", "10.1.2.3", "port", 3000));
    String dash = newServiceName("dash");

    ResponseEntity<JsonNode> response =
        importBackup(
            """
            version: 1
            aliases:
              %s:
                workstation: %s
                service: %s
            """
                .formatted(dash, WORKSTATION, grafana));

    assertThat(problems(response)).containsExactly("aliases." + dash + ".workstation");
  }

  @Test
  void aliasToAMissingRemoteServiceIsAddedWithoutARoute() {
    String dash = newServiceName("dash");

    ResponseEntity<JsonNode> response =
        importBackup(
            """
            version: 1
            aliases:
              %s:
                workstation: %s
                service: nothing-here
            """
                .formatted(dash, RemoteWorkstation.NAME));

    assertThat(entries(response)).containsExactly("add alias " + dash);
    assertThat(get("/api/workstation/aliases").getBody().valueStream())
        .filteredOn(alias -> alias.get("name").asString().equals(dash))
        .singleElement()
        .satisfies(alias -> assertThat(alias.get("state").asString()).isEqualTo("missing"));
  }

  @Test
  void aliasWithATargetThatIsNotADnsLabelIsRefused() {
    String dash = newServiceName("dash");

    ResponseEntity<JsonNode> response =
        importBackup(
            """
            version: 1
            aliases:
              %s:
                workstation: WS_B
                service: grafana
            """
                .formatted(dash));

    assertThat(problems(response)).containsExactly("aliases." + dash + ".workstation");
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
    String before = only(getYaml(BackupExportComponentTest.BACKUP_PATH).getBody(), names);
    delete(publicationPath(grafana));
    delete(instancesPath(grafana) + "/" + grafanaId);
    delete(containerBindingsPath(web) + "/" + webBinding);
    delete(publicationPath(docs));
    delete("/api/workstation/aliases/" + dash);
    delete(ROUTE_PROFILES_PATH + "/" + compress);
    assertThat(yaml(only(getYaml(BackupExportComponentTest.BACKUP_PATH).getBody(), names)))
        .containsEntry("services", Map.of())
        .containsEntry("aliases", Map.of())
        .containsEntry("route-profiles", Map.of());

    ResponseEntity<JsonNode> response = importBackup(before);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    String after = only(getYaml(BackupExportComponentTest.BACKUP_PATH).getBody(), names);
    assertThat(withoutExportedFrom(after)).isEqualTo(withoutExportedFrom(before));
  }
}
