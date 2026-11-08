package dev.palazon.lvhyard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** An export: the Backup of this Workstation's setup, as one YAML file. */
class BackupExportIT extends DashboardIT {

  static final String BACKUP_PATH = "/api/workstation/backup";

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
}
