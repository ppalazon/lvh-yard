package cc.lvhy.yard.dashboard;

import static cc.lvhy.yard.dashboard.BackupExportComponentTest.map;
import static cc.lvhy.yard.dashboard.BackupExportComponentTest.yaml;
import static org.assertj.core.api.Assertions.assertThat;

import cc.lvhy.yard.dashboard.consul.Consul.Registration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
    for (String key : List.of("route-profiles", "services")) {
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

  /** Each plan entry as one line, such as {@code skip service web: It exists already.}. */
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
    String path = BackupExportComponentTest.BACKUP_PATH + "?mode=" + mode;
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
  void newServiceGetsItsStaticInstanceAndStaysUnpublished() {
    String grafana = newServiceName("grafana");

    ResponseEntity<JsonNode> response =
        importBackup(
            """
            version: 2
            services:
              %s:
                sources:
                  - static-instance:
                      host: 10.1.2.3
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
                      tags: [green]
                  - static-instance:
                      host: 10.1.2.3
                      port: 3002
            """
                .formatted(grafana));

    assertThat(entries(response))
        .containsExactly(
            "skip service " + grafana + ": It exists already.",
            "skip static-instance 10.1.2.3:3000 of " + grafana + ": It is the same already.",
            "skip static-instance 10.1.2.3:3001 of " + grafana + ": It exists already.",
            "add static-instance 10.1.2.3:3002 of " + grafana,
            "skip instance-priority " + grafana + ": It exists already.");
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
            version: 2
            services:
              %s:
                sources:
                  - static-instance:
                      port: 3000
              %s:
                sources:
                  - static-instance:
                      host: %s
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
            version: 2
            services:
              %s:
                sources:
                  - container-binding:
                      compose: {project: shop, service: %s}
                      port: 8080
                  - container-binding:
                      container: %s-1
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
            "add container-binding " + web + "-1:80 of " + web,
            "skip instance-priority " + web + ": It exists already.");
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
            version: 2
            route-profiles:
              %s:
                compress: {}
            services:
              %s:
                publication:
                  scope: yard
                  route-profiles: [%s]
                sources:
                  - static-instance:
                      host: 10.1.2.3
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
        version: 2
        services:
          %s:
            sources:
              - static-instance:
                  host: 10.1.2.3
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
            version: 2
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
            version: 2
            services:
              %s:
                sources:
                  - static-instance:
                      host: 10.1.2.3
                      port: 3000
              %s:
                publication:
                  scope: everywhere
                  route-profiles: [missing-profile]
                sources:
                  - static-instance:
                      host: 10.1.2.3
                      port: 70000
            """
                .formatted(grafana, loki));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(problems(response))
        .containsExactly(
            "services." + loki + ".sources[0].static-instance.port",
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
            version: 2
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
  void olderVersionIsRefusedWithItsReason() {
    ResponseEntity<JsonNode> response = importBackup("version: 1\nservices: {}\n");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(problems(response)).containsExactly("version");
    assertThat(response.getBody().get("problems").get(0).get("reason").asString())
        .contains("older");
  }

  @Test
  void serviceThatOnlyForeignInstancesGiveGetsItsRecordAndSources() {
    String grafana = newServiceName("grafana");
    String id = "foreign-" + UUID.randomUUID();
    consul.register(new Registration(id, grafana, "10.9.8.7", 8080, List.of(), Map.of(), null));
    try {
      ResponseEntity<JsonNode> response =
          importBackup(
              """
              version: 2
              services:
                %s:
                  sources:
                    - static-instance:
                        host: 10.1.2.3
                        port: 3000
                  instance-priority: false
              """
                  .formatted(grafana));

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      JsonNode service = get(servicePath(grafana)).getBody();
      assertThat(service.get("created").asBoolean()).isTrue();
      assertThat(service.get("instances")).hasSize(2);
      assertThat(service.get("instancePriority").asBoolean()).isFalse();
    } finally {
      consul.deregister(id);
    }
  }

  @Test
  void emptyServiceIsAdded() {
    String notes = newServiceName("notes");

    ResponseEntity<JsonNode> response =
        importBackup(
            """
            version: 2
            services:
              %s:
                publication: none
                instance-priority: true
            """
                .formatted(notes));

    assertThat(entries(response))
        .containsExactly(
            "add service " + notes, "skip publication " + notes + ": It is Unpublished already.");
    JsonNode service = get(servicePath(notes)).getBody();
    assertThat(service.get("created").asBoolean()).isTrue();
    assertThat(service.get("empty").asBoolean()).isTrue();
  }

  @Test
  void orderAndToggleAreRestored() {
    String web = newServiceName("web");

    ResponseEntity<JsonNode> response =
        importBackup(
            """
            version: 2
            services:
              %s:
                instance-priority: false
                sources:
                  - alias:
                      workstation: %s
                      service: nothing-here
                  - static-instance:
                      host: 10.1.2.4
                      port: 3000
                  - container-binding:
                      compose: {project: shop, service: %s}
                      port: 8080
                  - static-instance:
                      host: 10.1.2.3
                      port: 3000
            """
                .formatted(web, RemoteWorkstation.NAME, web));

    assertThat(entries(response)).contains("add instance-priority " + web);
    JsonNode service = get(servicePath(web)).getBody();
    assertThat(service.get("instancePriority").asBoolean()).isFalse();
    assertThat(service.get("sources").valueStream().map(source -> source.get("kind").asString()))
        .containsExactly("alias", "static", "bound", "static");
    assertThat(service.get("sources").get(0).get("id").asString())
        .isEqualTo(RemoteWorkstation.NAME + "/nothing-here");
    assertThat(service.get("sources").get(2).get("id").asString())
        .isEqualTo("compose:shop:" + web + ":8080");
    String first = service.get("sources").get(1).get("id").asString();
    assertThat(
            service
                .get("instances")
                .valueStream()
                .filter(instance -> instance.get("id").asString().equals(first))
                .findFirst()
                .orElseThrow()
                .get("host")
                .asString())
        .isEqualTo("10.1.2.4");
  }

  @Test
  void existingServiceKeepsItsOrderAndToggle() {
    String web = newServiceName("web");
    String first = register(web, Map.of("host", "10.1.2.3", "port", 3000)).get("id").asString();
    String second = register(web, Map.of("host", "10.1.2.4", "port", 3000)).get("id").asString();

    ResponseEntity<JsonNode> response =
        importBackup(
            """
            version: 2
            services:
              %s:
                instance-priority: false
                sources:
                  - static-instance:
                      host: 10.1.2.4
                      port: 3000
                  - static-instance:
                      host: 10.1.2.3
                      port: 3000
            """
                .formatted(web));

    assertThat(entries(response))
        .contains("skip instance-priority " + web + ": It exists already.");
    JsonNode service = get(servicePath(web)).getBody();
    assertThat(service.get("instancePriority").asBoolean()).isTrue();
    assertThat(service.get("sources").valueStream().map(source -> source.get("id").asString()))
        .containsExactly(first, second);
  }

  @Test
  void refusedRouteProfileIsRefusedWithItsPlace() {
    String grafana = newServiceName("grafana");
    String auth = newServiceName("auth");

    ResponseEntity<JsonNode> response =
        importBackup(
            """
            version: 2
            route-profiles:
              %s:
                basicAuth:
                  usersFile: /etc/users
            services:
              %s:
                publication:
                  route-profiles: [%s]
                sources:
                  - static-instance:
                      host: 10.1.2.3
                      port: 3000
            """
                .formatted(auth, grafana, auth));

    assertThat(problems(response)).containsExactly("route-profiles." + auth);
    assertThat(get(ROUTE_PROFILES_PATH + "/" + auth).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void bindingATargetThatAnotherServiceFollowsIsAdded() {
    String web = newServiceName("web");
    String shop = newServiceName("shop");
    bindCompose(web, "shop", web, 8080);

    ResponseEntity<JsonNode> response =
        importBackup(
            """
            version: 2
            services:
              %s:
                sources:
                  - container-binding:
                      compose: {project: shop, service: %s}
                      port: 9090
            """
                .formatted(shop, web));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(get(servicePath(shop)).getBody().get("containerBindings")).hasSize(1);
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
  void unknownModeIsRefused() {
    ResponseEntity<JsonNode> response =
        postYaml(BackupExportComponentTest.BACKUP_PATH + "?mode=merge", "version: 2\n");

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
            version: 2
            services:
              %s:
                sources:
                  - alias:
                      workstation: %s
                      service: %s
            """
                .formatted(dash, WORKSTATION, grafana));

    assertThat(problems(response))
        .containsExactly("services." + dash + ".sources[0].alias.workstation");
  }

  @Test
  void aliasToAMissingRemoteServiceIsAddedAndGivesNoInstances() {
    String dash = newServiceName("dash");

    ResponseEntity<JsonNode> response =
        importBackup(
            """
            version: 2
            services:
              %s:
                sources:
                  - alias:
                      workstation: %s
                      service: nothing-here
            """
                .formatted(dash, RemoteWorkstation.NAME));

    assertThat(entries(response))
        .containsExactly(
            "add service " + dash,
            "add alias " + RemoteWorkstation.NAME + "/nothing-here of " + dash);
    assertThat(get(AliasesComponentTest.aliasesPath(dash)).getBody().valueStream())
        .singleElement()
        .satisfies(alias -> assertThat(alias.get("state").asString()).isEqualTo("missing"));
  }

  @Test
  void aliasWithATargetThatIsNotADnsLabelIsRefused() {
    String dash = newServiceName("dash");

    ResponseEntity<JsonNode> response =
        importBackup(
            """
            version: 2
            services:
              %s:
                sources:
                  - alias:
                      workstation: WS_B
                      service: grafana
            """
                .formatted(dash));

    assertThat(problems(response))
        .containsExactly("services." + dash + ".sources[0].alias.workstation");
  }

  @Test
  void aliasIsAddedAndAnExistingOneIsSkipped() {
    String grafana = newServiceName("grafana");
    String loki = newServiceName("loki");
    for (String service : List.of(grafana, loki)) {
      remote.register(service, Map.of("host", "10.9.8.7", "port", 3000));
      remote.publish(service, "yard");
    }
    String dash = newServiceName("dash");
    create(dash);
    post(
        AliasesComponentTest.aliasesPath(dash),
        Map.of("workstation", RemoteWorkstation.NAME, "service", grafana));

    ResponseEntity<JsonNode> response =
        importBackup(
            """
            version: 2
            services:
              %s:
                sources:
                  - alias:
                      workstation: %s
                      service: %s
                  - alias:
                      workstation: %s
                      service: %s
            """
                .formatted(dash, RemoteWorkstation.NAME, grafana, RemoteWorkstation.NAME, loki));

    assertThat(entries(response))
        .containsExactly(
            "skip service " + dash + ": It exists already.",
            "skip alias "
                + RemoteWorkstation.NAME
                + "/"
                + grafana
                + " of "
                + dash
                + ": It is the same already.",
            "add alias " + RemoteWorkstation.NAME + "/" + loki + " of " + dash,
            "skip instance-priority " + dash + ": It exists already.");
    assertThat(get(AliasesComponentTest.aliasesPath(dash)).getBody().findValuesAsString("service"))
        .containsExactly(grafana, loki);
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
    String before = only(getYaml(BackupExportComponentTest.BACKUP_PATH).getBody(), names);
    assertThat(map(map(yaml(before).get("services")).get(web)))
        .containsEntry("instance-priority", false);
    delete(servicePath(grafana));
    delete(servicePath(web));
    delete(servicePath(notes));
    delete(servicePath(docs));
    delete(servicePath(dash));
    delete(ROUTE_PROFILES_PATH + "/" + compress);
    assertThat(yaml(only(getYaml(BackupExportComponentTest.BACKUP_PATH).getBody(), names)))
        .containsEntry("services", Map.of())
        .containsEntry("route-profiles", Map.of());

    ResponseEntity<JsonNode> response = importBackup(before);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    String after = only(getYaml(BackupExportComponentTest.BACKUP_PATH).getBody(), names);
    assertThat(withoutExportedFrom(after)).isEqualTo(withoutExportedFrom(before));
  }
}
