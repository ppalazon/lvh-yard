package dev.palazon.lvhyard.cli;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class AliasTest extends YardTestSupport {

  @Test
  void aliasAddToAnExistingServiceSaysTheAliasJoinedIt() {
    dashboard.answer(
        "POST",
        "/api/workstation/services/web/aliases",
        201,
        """
        {"id": "ws-b/grafana", "workstation": "ws-b", "service": "grafana", "state": "active",
         "serviceCreated": false}
        """);

    Run run = yard("alias", "add", "web", "--workstation", "ws-b", "--service", "grafana");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out())
        .isEqualTo("Added the Alias to grafana on ws-b to the existing Service web.\n");
    assertThat(run.err()).isEmpty();
    dashboard.verifyCalled(
        "POST",
        "/api/workstation/services/web/aliases",
        """
        {"workstation": "ws-b", "service": "grafana", "create": true}
        """);
  }

  @Test
  void aliasAddThatGivesNoInstancesNowSaysWhy() {
    dashboard.answer(
        "POST",
        "/api/workstation/services/web/aliases",
        201,
        """
        {"id": "ws-b/grafana", "workstation": "ws-b", "service": "grafana",
         "state": "unavailable", "reason": "grafana has no Instance this Workstation can reach.",
         "serviceCreated": false}
        """);

    Run run = yard("alias", "add", "web", "--workstation", "ws-b", "--service", "grafana");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out())
        .isEqualTo("Added the Alias to grafana on ws-b to the existing Service web.\n");
    assertThat(run.err())
        .isEqualTo("Warning: grafana has no Instance this Workstation can reach.\n");
  }

  @Test
  void aliasAddNeedsTheWorkstationAndTheService() {
    Run noWorkstation = yard("alias", "add", "web", "--service", "grafana");
    Run noService = yard("alias", "add", "web", "--workstation", "ws-b");

    assertThat(noWorkstation.exitCode()).isEqualTo(CommandLine.ExitCode.USAGE);
    assertThat(noWorkstation.err()).contains("--workstation");
    assertThat(noService.exitCode()).isEqualTo(CommandLine.ExitCode.USAGE);
    assertThat(noService.err()).contains("--service");
  }

  @Test
  void aliasAddThatIsRefusedPrintsTheReason() {
    dashboard.answer(
        "POST",
        "/api/workstation/services/web/aliases",
        409,
        """
        {"type": "about:blank", "title": "Conflict", "status": 409,
         "detail": "web already has an Alias to ws-b/grafana."}
        """);

    Run run = yard("alias", "add", "web", "--workstation", "ws-b", "--service", "grafana");

    assertThat(run.exitCode()).isEqualTo(1);
    assertThat(run.err()).isEqualTo("web already has an Alias to ws-b/grafana.\n");
  }

  @Test
  void aliasAddWithANewServiceNameSaysTheServiceWasCreatedWithLocalScope() {
    dashboard.answer(
        "POST",
        "/api/workstation/services/dash/aliases",
        201,
        """
        {"id": "ws-b/grafana", "workstation": "ws-b", "service": "grafana", "state": "active",
         "serviceCreated": true}
        """);

    Run run = yard("alias", "add", "dash", "--workstation", "ws-b", "--service", "grafana");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out())
        .isEqualTo(
            "Created Service dash with Local Scope and added the Alias to grafana on ws-b.\n");
    dashboard.verifyCalled(
        "POST",
        "/api/workstation/services/dash/aliases",
        """
        {"workstation": "ws-b", "service": "grafana", "create": true}
        """);
  }

  @Test
  void aliasListShowsEachAliasOfTheServiceWithItsState() {
    dashboard.answer(
        "GET",
        "/api/workstation/services/web/aliases",
        200,
        """
        [{"id": "ws-b/grafana", "workstation": "ws-b", "service": "grafana",
          "state": "not-shared", "reason": "ws-b publishes grafana with Local Scope only."},
         {"id": "ws-c/grafana", "workstation": "ws-c", "service": "grafana", "state": "active"}]
        """);

    Run run = yard("alias", "list", "web");

    assertThat(run.exitCode()).isZero();
    assertThat(run.lines())
        .containsExactly(
            "grafana on ws-b: not-shared",
            "  ws-b publishes grafana with Local Scope only.",
            "grafana on ws-c: active");
  }

  @Test
  void aliasListWithoutAliasesSaysSo() {
    dashboard.answer("GET", "/api/workstation/services/web/aliases", 200, "[]");

    Run run = yard("alias", "list", "web");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out()).isEqualTo("web has no Aliases.\n");
  }

  @Test
  void aliasRemoveRemovesOneAliasOfTheService() {
    dashboard.answer("DELETE", "/api/workstation/services/web/aliases/ws-b/grafana", 204, null);

    Run run = yard("alias", "remove", "web", "--workstation", "ws-b", "--service", "grafana");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out()).isEqualTo("Removed the Alias to grafana on ws-b from web.\n");
    dashboard.verifyCalled("DELETE", "/api/workstation/services/web/aliases/ws-b/grafana");
  }
}
