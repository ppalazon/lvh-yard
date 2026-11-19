package cc.lvhy.yard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/** An import that the dashboard refuses: each problem names its place, and nothing changes. */
class BackupImportRefusalComponentTest extends BackupComponentTest {

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
  void unknownModeIsRefusedAndNothingChanges() {
    String grafana = newServiceName("grafana");

    ResponseEntity<JsonNode> response =
        postYaml(
            BACKUP_PATH + "?mode=merge",
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

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().get("detail").asString()).contains("merge");
    assertThat(get(servicePath(grafana)).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }
}
