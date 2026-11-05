package dev.palazon.lvhyard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** An export: the Backup of this Workstation's setup, as one YAML file. */
class BackupExportTest extends DashboardTest {

  static final String BACKUP_PATH = "/api/workstation/backup";

  @Test
  void exportIsVersionOneYamlFromThisWorkstation() {
    OffsetDateTime before = OffsetDateTime.now().minusSeconds(1);

    ResponseEntity<String> response = getYaml(BACKUP_PATH);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getContentType().isCompatibleWith(YAML)).isTrue();
    Map<String, Object> backup = yaml(response.getBody());
    assertThat(backup.get("version")).isEqualTo(1);
    Map<String, Object> exportedFrom = map(backup.get("exported-from"));
    assertThat(exportedFrom.get("workstation")).isEqualTo(WORKSTATION);
    assertThat(OffsetDateTime.parse((String) exportedFrom.get("at")))
        .isAfter(before)
        .isBefore(OffsetDateTime.now().plusSeconds(1));
  }

  @Test
  void publishedServiceHoldsItsScopePriorityAndRouteProfiles() {
    String compress = newServiceName("compress");
    putRouteProfiles(Map.of(compress, Map.of("compress", Map.of())));
    String grafana = newServiceName("grafana");
    register(grafana, Map.of("host", "10.1.2.3", "port", 3000));
    put(
        publicationPath(grafana),
        Map.of(
            "scope",
            "yard",
            "priority",
            List.of("bound", "static"),
            "profiles",
            List.of(compress)));

    Map<String, Object> backup = exported();

    assertThat(map(map(backup.get("services")).get(grafana)).get("publication"))
        .isEqualTo(
            Map.of(
                "scope", "yard",
                "priority", List.of("bound", "static"),
                "route-profiles", List.of(compress)));
    assertThat(map(backup.get("route-profiles")).get(compress))
        .isEqualTo(Map.of("compress", Map.of()));
  }

  @Test
  void unpublishedServiceHasPublicationNone() {
    String grafana = newServiceName("grafana");
    register(grafana, Map.of("host", "10.1.2.3", "port", 3000));

    assertThat(map(map(exported().get("services")).get(grafana)).get("publication"))
        .isEqualTo("none");
  }

  @Test
  void staticInstanceHoldsItsPortCheckTagsAndMeta() {
    String grafana = newServiceName("grafana");
    register(
        grafana,
        Map.of(
            "host", "10.1.2.3",
            "port", 3000,
            "check", Map.of("path", "/health", "interval", "5s", "timeout", "1500ms"),
            "tags", List.of("blue", "100"),
            "meta", Map.of("team", "core", "version", "2")));

    assertThat(map(map(exported().get("services")).get(grafana)).get("static-instances"))
        .isEqualTo(
            List.of(
                Map.of(
                    "host", "10.1.2.3",
                    "port", 3000,
                    "check",
                        Map.of(
                            "type",
                            "http",
                            "path",
                            "/health",
                            "interval",
                            "5s",
                            "timeout",
                            "1500ms"),
                    "tags", List.of("blue", "100"),
                    "meta", Map.of("team", "core", "version", "2"))));
  }

  @Test
  void staticInstanceLeavesOutEmptyTagsAndMetaAndTheLanAddress() {
    String grafana = newServiceName("grafana");
    register(grafana, Map.of("port", 3000));

    assertThat(map(map(exported().get("services")).get(grafana)).get("static-instances"))
        .isEqualTo(
            List.of(
                Map.of(
                    "port",
                    3000,
                    "check",
                    Map.of("type", "tcp", "interval", "10s", "timeout", "2s"))));
  }

  @Test
  void containerBindingsAreHeldButNotTheirBoundInstances() {
    String web = newServiceName("web");
    String name = newContainerName();
    startContainer(name, 80, false);
    bind(web, name, 80);
    bindCompose(web, "shop", web, 8080);
    await()
        .atMost(Duration.ofSeconds(15))
        .until(() -> get(servicePath(web)).getBody().get("instances").size() == 1);

    Map<String, Object> entry = map(map(exported().get("services")).get(web));

    assertThat(entry.get("container-bindings"))
        .isEqualTo(
            List.of(
                Map.of("compose", Map.of("project", "shop", "service", web), "port", 8080),
                Map.of("container", name, "port", 80)));
    assertThat(entry).doesNotContainKey("static-instances");
  }

  @Test
  void aliasesAreHeldButNotRemoteServices() {
    String grafana = newServiceName("grafana");
    remote.register(grafana, Map.of("host", "10.9.8.7", "port", 3000));
    remote.publish(grafana, "yard");
    String alias = newServiceName("dash");
    put(
        "/api/workstation/aliases/" + alias,
        Map.of("workstation", RemoteWorkstation.NAME, "service", grafana));

    Map<String, Object> backup = exported();

    assertThat(map(backup.get("aliases")).get(alias))
        .isEqualTo(Map.of("workstation", RemoteWorkstation.NAME, "service", grafana));
    assertThat(map(backup.get("services"))).doesNotContainKey(grafana);
  }

  @Test
  void lvhYardsOwnServicesAreHeldLikeOthers() {
    assertThat(map(exported().get("services"))).containsKeys("yard", "consul-ui", "traefik-admin");
    assertThat(map(exported().get("services"))).doesNotContainKey("consul");
  }

  private Map<String, Object> exported() {
    return yaml(getYaml(BACKUP_PATH).getBody());
  }

  static final MediaType YAML = MediaType.parseMediaType("application/yaml");

  @SuppressWarnings("unchecked")
  static Map<String, Object> yaml(String text) {
    return (Map<String, Object>) new Yaml(new SafeConstructor(new LoaderOptions())).load(text);
  }

  @SuppressWarnings("unchecked")
  static Map<String, Object> map(Object value) {
    return (Map<String, Object>) value;
  }
}
