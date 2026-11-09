package dev.palazon.lvhyard.cli;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ServiceCommandTest extends YardTestSupport {

  @Test
  void serviceCreateCreatesAnEmptyUnpublishedService() {
    dashboard.answer(
        "POST",
        "/api/workstation/services",
        201,
        """
        {"workstation": "ws-a", "name": "web", "state": "unpublished", "created": true,
         "empty": true, "sources": [], "instancePriority": true, "instances": [], "containerBindings": [], "warnings": []}
        """);

    Run run = yard("service", "create", "web");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out())
        .isEqualTo(
            "Created web. It is empty and Unpublished: attach Instances with lvh-yard register.\n");
    dashboard.verifyCalled(
        "POST",
        "/api/workstation/services",
        """
        {"name": "web"}
        """);
  }

  @Test
  void serviceCreateOfANameInUseSaysSo() {
    dashboard.answer(
        "POST",
        "/api/workstation/services",
        409,
        """
        {"type": "about:blank", "title": "Conflict", "status": 409,
         "detail": "This Workstation already has a Service web."}
        """);

    Run run = yard("service", "create", "web");

    assertThat(run.exitCode()).isNotZero();
    assertThat(run.err()).isEqualTo("This Workstation already has a Service web.\n");
  }

  @Test
  void serviceDeleteDeletesTheService() {
    dashboard.answer("DELETE", "/api/workstation/services/web", 204, null);

    Run run = yard("service", "delete", "web");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out())
        .isEqualTo(
            "Deleted web with its Publication, Static Instances and Container Bindings. Its"
                + " containers keep running.\n");
    dashboard.verifyCalled("DELETE", "/api/workstation/services/web");
  }

  @Test
  void serviceDeleteOfAMissingServiceSaysSo() {
    dashboard.answer(
        "DELETE",
        "/api/workstation/services/wbe",
        404,
        """
        {"type": "about:blank", "title": "Not Found", "status": 404,
         "detail": "This Workstation has no Service wbe."}
        """);

    Run run = yard("service", "delete", "wbe");

    assertThat(run.exitCode()).isNotZero();
    assertThat(run.err()).isEqualTo("This Workstation has no Service wbe.\n");
  }

  @Test
  void serviceWithoutASubcommandPrintsItsUsage() {
    Run run = yard("service");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out()).contains("create").contains("delete");
  }
}
