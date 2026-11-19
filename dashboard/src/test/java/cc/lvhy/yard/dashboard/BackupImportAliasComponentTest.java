package cc.lvhy.yard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/** An import of aliases, in add mode and in update mode. */
class BackupImportAliasComponentTest extends BackupComponentTest {

  @Test
  void aliasToThisWorkstationIsRefused() {
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
                      service: grafana
            """
                .formatted(dash, WORKSTATION));

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
  void aliasOfTheFileJoinsTheAliasesThereAndKeepsThemInUpdateMode() {
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
        postYaml(
            BACKUP_PATH + "?mode=update",
            """
            version: 2
            services:
              %s:
                sources:
                  - alias:
                      workstation: %s
                      service: %s
            """
                .formatted(dash, RemoteWorkstation.NAME, loki));

    assertThat(entries(response))
        .containsExactly(
            "skip service " + dash + ": It exists already.",
            "add alias " + RemoteWorkstation.NAME + "/" + loki + " of " + dash);
    assertThat(get(AliasesComponentTest.aliasesPath(dash)).getBody().findValuesAsString("service"))
        .containsExactly(grafana, loki);
  }
}
