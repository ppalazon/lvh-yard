package cc.lvhy.yard.dashboard;

import static cc.lvhy.yard.dashboard.BackupExportComponentTest.map;
import static cc.lvhy.yard.dashboard.BackupExportComponentTest.sources;
import static cc.lvhy.yard.dashboard.BackupExportComponentTest.yaml;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import cc.lvhy.yard.dashboard.docker.Docker.Container;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;

@TestPropertySource(properties = "YARD_INSTANCE_HOST=192.0.2.42")
class InstanceHostComponentTest extends DashboardComponentTest {

  private static final String NATIVE_HOST = "192.0.2.42";

  @Test
  void omittedAndBlankHostsUseTheNativeHostWhileExplicitHostsStayUnchanged() {
    String web = newServiceName("web");

    assertThat(register(web, Map.of("port", 3000)).get("host").asString()).isEqualTo(NATIVE_HOST);
    assertThat(register(web, Map.of("host", " ", "port", 3001)).get("host").asString())
        .isEqualTo(NATIVE_HOST);
    assertThat(register(web, Map.of("host", lanAddress(), "port", 3002)).get("host").asString())
        .isEqualTo(lanAddress());
    assertThat(getPage("/services/" + WORKSTATION + "/" + web + "/instances/new").getBody())
        .contains("placeholder=\"" + NATIVE_HOST + "\"");
  }

  @Test
  void backupOmitsTheNativeHostButKeepsAnExplicitConsulAddress() {
    String nativeService = newServiceName("native");
    String agentService = newServiceName("agent");
    register(nativeService, Map.of("port", 3000));
    register(agentService, Map.of("host", lanAddress(), "port", 3000));

    Map<String, Object> backup = yaml(getYaml(BackupExportComponentTest.BACKUP_PATH).getBody());

    assertThat(sources(backup, nativeService, "static-instance")).hasSize(1);
    assertThat(map(sources(backup, nativeService, "static-instance").getFirst()))
        .doesNotContainKey("host");
    assertThat(map(sources(backup, agentService, "static-instance").getFirst()))
        .containsEntry("host", lanAddress());
  }

  @Test
  void backupImportResolvesAnOmittedHostToTheNativeHost() {
    String web = newServiceName("web");

    assertThat(
            postYaml(
                    BackupImportComponentTest.IMPORT_PATH,
                    """
                    version: 3
                    services:
                      %s:
                        sources:
                          - static-instance:
                              port: 3000
                    """
                        .formatted(web))
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(onlyInstance(web).get("host").asString()).isEqualTo(NATIVE_HOST);
  }

  @Test
  void wildcardPublishedPortUsesTheNativeHostForYardScopeAndLoopbackForLocalScope() {
    String web = newServiceName("web");
    String name = newContainerName();
    Container container = startContainer(name, 80, true);
    int hostPort = hostPort(container, 80);
    consul.listen(NATIVE_HOST, hostPort);
    bind(web, name, 80);

    await()
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(
            () -> assertThat(onlyInstance(web).get("host").asString()).isEqualTo(NATIVE_HOST));
    put(publicationPath(web), Map.of("scope", "yard"));
    assertThat(onlyInstance(web).get("port").asInt()).isEqualTo(hostPort);
    assertThat(onlyInstance(web).get("host").asString()).isEqualTo(NATIVE_HOST);
    assertThat(
            TraefikConfigurationComponentTest.serverUrlsAt(
                get("/traefik/configuration").getBody().get("http"), web + ".lvh.test"))
        .containsExactly("http://" + NATIVE_HOST + ":" + hostPort);

    put(publicationPath(web), Map.of("scope", "local"));

    assertThat(onlyInstance(web).get("host").asString()).isEqualTo("127.0.0.1");
    assertThat(
            TraefikConfigurationComponentTest.serverUrlsAt(
                get("/traefik/configuration").getBody().get("http"), web + ".lvh.test"))
        .containsExactly("http://127.0.0.1:" + hostPort);
  }

  private JsonNode onlyInstance(String service) {
    JsonNode instances = get(servicePath(service)).getBody().get("instances");
    assertThat(instances).hasSize(1);
    return instances.get(0);
  }
}
