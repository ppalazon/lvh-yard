package cc.lvhy.yard.dashboard;

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

/** An export: the Backup of this Workstation's setup, as one YAML file. */
class BackupExportComponentTest extends BackupComponentTest {

  static final MediaType YAML = MediaType.parseMediaType("application/yaml");

  private Map<String, Object> exported() {
    return yaml(getYaml(BACKUP_PATH).getBody());
  }

  @Test
  void exportIsVersionThreeYamlFromThisWorkstation() {
    OffsetDateTime before = OffsetDateTime.now().minusSeconds(1);

    ResponseEntity<String> response = getYaml(BACKUP_PATH);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getContentType().isCompatibleWith(YAML)).isTrue();
    Map<String, Object> backup = yaml(response.getBody());
    assertThat(backup.get("version")).isEqualTo(3);
    Map<String, Object> exportedFrom = map(backup.get("exported-from"));
    assertThat(exportedFrom.get("workstation")).isEqualTo(WORKSTATION);
    assertThat(OffsetDateTime.parse((String) exportedFrom.get("at")))
        .isAfter(before)
        .isBefore(OffsetDateTime.now().plusSeconds(1));
  }

  @Test
  void publishedServiceHoldsItsScopeAndRouteProfiles() {
    String compress = newServiceName("compress");
    putRouteProfiles(Map.of(compress, Map.of("compress", Map.of())));
    String grafana = newServiceName("grafana");
    register(grafana, Map.of("host", "10.1.2.3", "port", 3000));
    put(publicationPath(grafana), Map.of("scope", "yard", "profiles", List.of(compress)));

    Map<String, Object> backup = exported();

    assertThat(map(map(backup.get("services")).get(grafana)).get("publication"))
        .isEqualTo(Map.of("scope", "yard", "route-profiles", List.of(compress)));
    assertThat(map(backup.get("route-profiles")).get(compress))
        .isEqualTo(Map.of("compress", Map.of()));
  }

  @Test
  void mutualTlsOfAPublicationIsExported() {
    String grafana = newServiceName("grafana");
    register(grafana, Map.of("host", "10.1.2.3", "port", 3000));
    put(publicationPath(grafana), Map.of("scope", "yard", "mutualTls", true));

    assertThat(map(map(exported().get("services")).get(grafana)).get("publication"))
        .isEqualTo(Map.of("scope", "yard", "mutual-tls", true));
  }

  @Test
  void clientCertificateProfileIsExported() {
    String certificate = newServiceName("certificate");
    putRouteProfiles(Map.of(certificate, Map.of("clientCertificate", Map.of())));

    assertThat(map(exported().get("route-profiles")).get(certificate))
        .isEqualTo(Map.of("clientCertificate", Map.of()));
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

    assertThat(sources(exported(), grafana, "static-instance"))
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

    assertThat(sources(exported(), grafana, "static-instance"))
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

    Map<String, Object> backup = exported();

    assertThat(sources(backup, web, "container-binding"))
        .isEqualTo(
            List.of(
                Map.of("container", name, "port", 80),
                Map.of("compose", Map.of("project", "shop", "service", web), "port", 8080)));
    assertThat(sources(backup, web, "static-instance")).isEmpty();
  }

  @Test
  void lvhYardsOwnServicesAreHeldLikeOthers() {
    Map<String, Object> services = map(exported().get("services"));

    assertThat(services).containsKeys("yard", "consul-ui", "traefik-admin");
    assertThat(services).doesNotContainKey("consul");
    assertThat(map(services.get("yard")).get("publication")).isEqualTo(Map.of("scope", "local"));
  }

  @Test
  void accessLogOfAPublicationIsExported() {
    String grafana = newServiceName("grafana");
    register(grafana, Map.of("host", "10.1.2.3", "port", 3000));
    put(publicationPath(grafana), Map.of("scope", "yard", "accessLog", true));

    assertThat(map(map(exported().get("services")).get(grafana)).get("publication"))
        .isEqualTo(Map.of("scope", "yard", "access-log", true));
  }

  @Test
  void aliasesAreHeldButNotRemoteServices() {
    String grafana = newServiceName("grafana");
    remote.register(grafana, Map.of("host", "10.9.8.7", "port", 3000));
    remote.publish(grafana, "yard");
    String dash = newServiceName("dash");
    create(dash);
    post(
        AliasesComponentTest.aliasesPath(dash),
        Map.of("workstation", RemoteWorkstation.NAME, "service", grafana));

    Map<String, Object> backup = exported();

    assertThat(sources(backup, dash, "alias"))
        .isEqualTo(List.of(Map.of("workstation", RemoteWorkstation.NAME, "service", grafana)));
    assertThat(map(backup.get("services"))).doesNotContainKey(grafana);
  }

  @Test
  void emptyServiceIsHeld() {
    String notes = newServiceName("notes");
    create(notes);

    assertThat(map(map(exported().get("services")).get(notes)))
        .isEqualTo(Map.of("publication", "none", "instance-priority", true));
  }

  @Test
  void sourcesAreHeldInTheirOrderWithTheToggle() {
    String grafana = newServiceName("grafana");
    remote.register(grafana, Map.of("host", "10.9.8.7", "port", 3000));
    remote.publish(grafana, "yard");
    String web = newServiceName("web");
    String first = register(web, Map.of("host", "10.1.2.3", "port", 3000)).get("id").asString();
    String bound = bindCompose(web, "shop", web, 8080).get("id").asString();
    post(
        AliasesComponentTest.aliasesPath(web),
        Map.of("workstation", RemoteWorkstation.NAME, "service", grafana));
    String second = register(web, Map.of("host", "10.1.2.4", "port", 3000)).get("id").asString();
    put(
        instancePriorityPath(web),
        Map.of(
            "order",
            List.of(RemoteWorkstation.NAME + "/" + grafana, second, bound, first),
            "on",
            false));

    Map<String, Object> entry = map(map(exported().get("services")).get(web));

    assertThat(entry.get("instance-priority")).isEqualTo(false);
    assertThat(entry.get("sources"))
        .asList()
        .extracting(source -> map(source).keySet().iterator().next())
        .containsExactly("alias", "static-instance", "container-binding", "static-instance");
    assertThat(sources(exported(), web, "static-instance"))
        .extracting(instance -> map(instance).get("host"))
        .containsExactly("10.1.2.4", "10.1.2.3");
  }
}
