package dev.palazon.lvhyard.dashboard;

import static dev.palazon.lvhyard.dashboard.BackupImportIT.entries;
import static dev.palazon.lvhyard.dashboard.BackupImportIT.problems;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/** An import in update mode: it also overwrites what exists with the values of the file. */
class BackupUpdateIT extends DashboardIT {

  static final String UPDATE_PATH = BackupExportIT.BACKUP_PATH + "?mode=update";

  @Test
  void staticInstanceGetsTheCheckTagsAndMetaOfTheFileAndKeepsItsId() {
    String grafana = newServiceName("grafana");
    String id =
        register(grafana, Map.of("host", "10.1.2.3", "port", 3000, "tags", List.of("blue")))
            .get("id")
            .asString();

    ResponseEntity<JsonNode> response =
        update(
            """
            version: 1
            services:
              %s:
                static-instances:
                  - host: 10.1.2.3
                    port: 3000
                    check: {type: http, path: /health, interval: 5s, timeout: 1s}
                    tags: [green]
                    meta: {team: data}
            """
                .formatted(grafana));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(entries(response))
        .containsExactly(
            "skip service " + grafana + ": It exists already.",
            "update static-instance 10.1.2.3:3000 of " + grafana);
    JsonNode instances = get(servicePath(grafana)).getBody().get("instances");
    assertThat(instances).hasSize(1);
    JsonNode instance = instances.get(0);
    assertThat(instance.get("id").asString()).isEqualTo(id);
    assertThat(instance.get("tags").valueStream().map(JsonNode::asString)).containsExactly("green");
    assertThat(instance.get("meta").get("team").asString()).isEqualTo("data");
    assertThat(instance.get("check").get("path").asString()).isEqualTo("/health");
  }

  @Test
  void publicationGetsEveryPartOfTheFile() {
    String grafana = newServiceName("grafana");
    String compress = newServiceName("compress");
    register(grafana, Map.of("host", "10.1.2.3", "port", 3000));
    put(publicationPath(grafana), Map.of("scope", "local", "priority", List.of("static")));

    ResponseEntity<JsonNode> response =
        update(
            """
            version: 1
            route-profiles:
              %s:
                compress: {}
            services:
              %s:
                publication:
                  scope: yard
                  route-profiles: [%s]
            """
                .formatted(compress, grafana, compress));

    assertThat(entries(response))
        .containsExactly(
            "add route-profile " + compress,
            "skip service " + grafana + ": It exists already.",
            "update publication " + grafana);
    JsonNode service = get(servicePath(grafana)).getBody();
    assertThat(service.get("publication").get("scope").asString()).isEqualTo("yard");
    assertThat(service.get("priority").valueStream().map(JsonNode::asString))
        .containsExactly("static", "bound", "alias");
    assertThat(service.get("profiles").valueStream().map(JsonNode::asString))
        .containsExactly(compress);
  }

  @Test
  void samePublicationIsASkip() {
    String grafana = newServiceName("grafana");
    register(grafana, Map.of("host", "10.1.2.3", "port", 3000));
    put(publicationPath(grafana), Map.of("scope", "yard"));

    ResponseEntity<JsonNode> response =
        update(
            """
            version: 1
            services:
              %s:
                publication:
                  scope: yard
            """
                .formatted(grafana));

    assertThat(entries(response))
        .containsExactly(
            "skip service " + grafana + ": It exists already.",
            "skip publication " + grafana + ": It is the same already.");
  }

  @Test
  void publicationNoneUnpublishesTheService() {
    String grafana = newServiceName("grafana");
    register(grafana, Map.of("host", "10.1.2.3", "port", 3000));
    put(publicationPath(grafana), Map.of("scope", "yard"));

    ResponseEntity<JsonNode> response =
        update(
            """
            version: 1
            services:
              %s:
                publication: none
            """
                .formatted(grafana));

    assertThat(entries(response))
        .containsExactly(
            "skip service " + grafana + ": It exists already.", "update publication " + grafana);
    assertThat(get(servicePath(grafana)).getBody().get("state").asString())
        .isEqualTo("unpublished");
  }

  @Test
  void missingPublicationKeyKeepsTheExistingPublication() {
    String grafana = newServiceName("grafana");
    register(grafana, Map.of("host", "10.1.2.3", "port", 3000));
    put(publicationPath(grafana), Map.of("scope", "yard"));

    ResponseEntity<JsonNode> response =
        update(
            """
            version: 1
            services:
              %s:
                static-instances:
                  - host: 10.1.2.3
                    port: 3000
                    tags: [green]
            """
                .formatted(grafana));

    assertThat(entries(response))
        .containsExactly(
            "skip service " + grafana + ": It exists already.",
            "update static-instance 10.1.2.3:3000 of " + grafana);
    assertThat(get(servicePath(grafana)).getBody().get("publication").get("scope").asString())
        .isEqualTo("yard");
  }

  @Test
  void routeProfileGetsTheDefinitionOfTheFile() {
    String headers = newServiceName("headers");
    String same = newServiceName("same");
    putRouteProfiles(
        Map.of(
            headers,
            Map.of("headers", Map.of("customRequestHeaders", Map.of("X-Team", "blue"))),
            same,
            Map.of("compress", Map.of())));

    ResponseEntity<JsonNode> response =
        update(
            """
            version: 1
            route-profiles:
              %s:
                headers:
                  customRequestHeaders: {X-Team: green}
              %s:
                compress: {}
            """
                .formatted(headers, same));

    assertThat(entries(response))
        .containsExactly(
            "update route-profile " + headers,
            "skip route-profile " + same + ": It is the same already.");
    assertThat(
            get(ROUTE_PROFILES_PATH + "/" + headers)
                .getBody()
                .at("/definition/headers/customRequestHeaders/X-Team")
                .asString())
        .isEqualTo("green");
  }

  @Test
  void refusedRouteProfileOverwriteIsRefusedWithItsPlace() {
    String auth = newServiceName("auth");
    putRouteProfiles(Map.of(auth, Map.of("compress", Map.of())));

    ResponseEntity<JsonNode> response =
        update(
            """
            version: 1
            route-profiles:
              %s:
                basicAuth:
                  usersFile: /etc/users
            """
                .formatted(auth));

    assertThat(problems(response)).containsExactly("route-profiles." + auth);
    assertThat(get(ROUTE_PROFILES_PATH + "/" + auth).getBody().at("/type").asString())
        .isEqualTo("compress");
  }

  @Test
  void aliasGetsTheTargetOfTheFile() {
    String grafana = newServiceName("grafana");
    String loki = newServiceName("loki");
    for (String service : List.of(grafana, loki)) {
      remote.register(service, Map.of("host", "10.9.8.7", "port", 3000));
    }
    String dash = newServiceName("dash");
    put(
        "/api/workstation/aliases/" + dash,
        Map.of("workstation", RemoteWorkstation.NAME, "service", grafana));

    ResponseEntity<JsonNode> response =
        update(
            """
            version: 1
            aliases:
              %s:
                workstation: %s
                service: %s
            """
                .formatted(dash, RemoteWorkstation.NAME, loki));

    assertThat(entries(response)).containsExactly("update alias " + dash);
    JsonNode alias =
        get("/api/workstation/aliases")
            .getBody()
            .valueStream()
            .filter(candidate -> candidate.get("name").asString().equals(dash))
            .findFirst()
            .orElseThrow();
    assertThat(alias.findValuesAsString("service")).contains(loki);
  }

  @Test
  void aliasOverwriteToAMissingRemoteServiceIsApplied() {
    String grafana = newServiceName("grafana");
    remote.register(grafana, Map.of("host", "10.9.8.7", "port", 3000));
    String dash = newServiceName("dash");
    put(
        "/api/workstation/aliases/" + dash,
        Map.of("workstation", RemoteWorkstation.NAME, "service", grafana));

    ResponseEntity<JsonNode> response =
        update(
            """
            version: 1
            aliases:
              %s:
                workstation: %s
                service: nothing-here
            """
                .formatted(dash, RemoteWorkstation.NAME));

    assertThat(entries(response)).containsExactly("update alias " + dash);
  }

  @Test
  void containerBindingMatchIsASkip() {
    String web = newServiceName("web");
    bindCompose(web, "shop", web, 8080);

    ResponseEntity<JsonNode> response =
        update(
            """
            version: 1
            services:
              %s:
                container-bindings:
                  - compose: {project: shop, service: %s}
                    port: 8080
            """
                .formatted(web, web));

    assertThat(entries(response))
        .containsExactly(
            "skip service " + web + ": It exists already.",
            "skip container-binding compose:shop:"
                + web
                + ":8080 of "
                + web
                + ": It is the same already.");
  }

  ResponseEntity<JsonNode> update(String backup) {
    return postYaml(UPDATE_PATH, backup);
  }
}
