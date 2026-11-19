package cc.lvhy.yard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/** An import in update mode: it also overwrites what exists with the values of the file. */
class BackupUpdateComponentTest extends BackupComponentTest {

  static final String UPDATE_PATH = BACKUP_PATH + "?mode=update";

  ResponseEntity<JsonNode> update(String backup) {
    return postYaml(UPDATE_PATH, backup);
  }

  @Test
  void orderAndToggleOfTheFileComeFirstAndOtherSourcesFollow() {
    String web = newServiceName("web");
    String first = register(web, Map.of("host", "10.1.2.3", "port", 3000)).get("id").asString();
    String second = register(web, Map.of("host", "10.1.2.4", "port", 3000)).get("id").asString();
    String other = register(web, Map.of("host", "10.1.2.5", "port", 3000)).get("id").asString();
    put(instancePriorityPath(web), Map.of("order", List.of(other, first, second)));

    ResponseEntity<JsonNode> response =
        update(
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
        .containsExactly(
            "skip service " + web + ": It exists already.",
            "skip static-instance 10.1.2.4:3000 of " + web + ": It is the same already.",
            "skip static-instance 10.1.2.3:3000 of " + web + ": It is the same already.",
            "update instance-priority " + web);
    JsonNode service = get(servicePath(web)).getBody();
    assertThat(service.get("instancePriority").asBoolean()).isFalse();
    assertThat(service.get("sources").valueStream().map(source -> source.get("id").asString()))
        .containsExactly(second, first, other);
  }

  @Test
  void sameOrderAndToggleIsASkip() {
    String web = newServiceName("web");
    register(web, Map.of("host", "10.1.2.3", "port", 3000));
    register(web, Map.of("host", "10.1.2.4", "port", 3000));

    ResponseEntity<JsonNode> response =
        update(
            """
            version: 2
            services:
              %s:
                instance-priority: true
                sources:
                  - static-instance:
                      host: 10.1.2.3
                      port: 3000
                  - static-instance:
                      host: 10.1.2.4
                      port: 3000
            """
                .formatted(web));

    assertThat(entries(response))
        .containsExactly(
            "skip service " + web + ": It exists already.",
            "skip static-instance 10.1.2.3:3000 of " + web + ": It is the same already.",
            "skip static-instance 10.1.2.4:3000 of " + web + ": It is the same already.",
            "skip instance-priority " + web + ": It is the same already.");
  }

  @Test
  void aFileThatNamesTheSourcesInAnotherOrderUpdatesTheOrderAndKeepsTheToggle() {
    String web = newServiceName("web");
    String first = register(web, Map.of("host", "10.1.2.3", "port", 3000)).get("id").asString();
    String second = register(web, Map.of("host", "10.1.2.4", "port", 3000)).get("id").asString();

    ResponseEntity<JsonNode> response =
        update(
            """
            version: 2
            services:
              %s:
                sources:
                  - static-instance:
                      host: 10.1.2.4
                      port: 3000
                  - static-instance:
                      host: 10.1.2.3
                      port: 3000
            """
                .formatted(web));

    assertThat(entries(response)).last().isEqualTo("update instance-priority " + web);
    JsonNode service = get(servicePath(web)).getBody();
    assertThat(service.get("instancePriority").asBoolean()).isTrue();
    assertThat(service.get("sources").valueStream().map(source -> source.get("id").asString()))
        .containsExactly(second, first);
  }

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
            version: 2
            services:
              %s:
                sources:
                  - static-instance:
                      host: 10.1.2.3
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
            version: 2
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
            version: 2
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
            version: 2
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
            version: 2
            services:
              %s:
                sources:
                  - static-instance:
                      host: 10.1.2.3
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
            version: 2
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
            version: 2
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
}
