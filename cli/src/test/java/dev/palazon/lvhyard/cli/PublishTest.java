package dev.palazon.lvhyard.cli;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class PublishTest extends YardTestSupport {

  @Test
  void publishPublishesWithLocalScopeAndShowsTheHostname() {
    dashboard.answer(
        "PUT",
        "/api/workstation/services/web/publication",
        200,
        """
        {"workstation": "ws-a", "name": "web", "state": "unhealthy", "created": true, "empty": false, "sources": [], "instancePriority": true, "publication": {"scope": "local", "accessLog": false},
         "hostname": "web.lvh.me", "containerBindings": [], "warnings": [], "instances": [
            {"id": "7b1c", "host": "10.1.2.3", "port": 8080, "health": "critical",
             "tags": [], "meta": {}, "checks": [], "outOfRange": false}]}
        """);

    Run run = yard("publish", "web");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out())
        .isEqualTo("Published web at web.lvh.me, with Local Scope. It is unhealthy.\n");
    dashboard.verifyCalled("PUT", "/api/workstation/services/web/publication");
  }

  @Test
  void publishWithYardScopeSendsTheScope() {
    dashboard.answer(
        "PUT",
        "/api/workstation/services/web/publication",
        200,
        """
        {"workstation": "ws-a", "name": "web", "state": "healthy", "created": true, "empty": false, "sources": [], "instancePriority": true, "publication": {"scope": "yard", "accessLog": false},
         "hostname": "web.lvh.me", "containerBindings": [],
          "warnings": [], "instances": [
            {"id": "7b1c", "host": "10.1.2.3", "port": 8080, "health": "passing",
             "tags": [], "meta": {}, "checks": [], "outOfRange": false}]}
        """);

    Run run = yard("publish", "web", "--scope", "yard");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out())
        .isEqualTo(
            "Published web at web.lvh.me, with Yard Scope. Other Workstations reach it at"
                + " web.ws-a.<base-domain>. It is healthy.\n");
    dashboard.verifyCalled(
        "PUT",
        "/api/workstation/services/web/publication",
        """
        {"scope": "yard"}
        """);
  }

  @Test
  void publishWithProfilesSendsThemInOrderAndPrintsTheAuthWarning() {
    dashboard.answer(
        "PUT",
        "/api/workstation/services/admin/publication",
        200,
        """
        {"workstation": "ws-a", "name": "admin", "state": "healthy", "created": true, "empty": false, "sources": [], "instancePriority": true,
         "publication": {"scope": "local", "accessLog": false}, "hostname": "admin.lvh.me", "containerBindings": [],
         "profiles": ["basic-auth", "cors"],
         "warnings": [
            {"kind": "route-profile-auth", "severity": "risk",
             "message": "The Route Profile basic-auth asks who the user is only on the hostname."}],
         "instances": []}
        """);

    Run run = yard("publish", "admin", "--profile", "basic-auth", "--profile", "cors");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out()).contains("Route Profiles: basic-auth, cors.");
    assertThat(run.err())
        .isEqualTo(
            "risk: The Route Profile basic-auth asks who the user is only on the hostname.\n");
    dashboard.verifyCalled(
        "PUT",
        "/api/workstation/services/admin/publication",
        """
        {"profiles": ["basic-auth", "cors"]}
        """);
  }

  @Test
  void publishWithNoProfilesSendsAnEmptyList() {
    dashboard.answer(
        "PUT",
        "/api/workstation/services/admin/publication",
        200,
        """
        {"workstation": "ws-a", "name": "admin", "state": "healthy", "created": true, "empty": false, "sources": [], "instancePriority": true,
         "publication": {"scope": "local", "accessLog": false}, "hostname": "admin.lvh.me", "containerBindings": [],
         "warnings": [], "instances": []}
        """);

    Run run = yard("publish", "admin", "--no-profiles");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out()).doesNotContain("Route Profiles");
    dashboard.verifyCalled(
        "PUT",
        "/api/workstation/services/admin/publication",
        """
        {"profiles": []}
        """);
  }

  @Test
  void publishRefusesProfilesWithNoProfiles() {
    Run run = yard("publish", "admin", "--profile", "cors", "--no-profiles");

    assertThat(run.exitCode()).isEqualTo(CommandLine.ExitCode.USAGE);
    assertThat(run.err()).contains("--profile").contains("--no-profiles");
  }

  private static final String WEB_WITH_TWO_SOURCES =
      """
      {"workstation": "ws-a", "name": "web", "state": "healthy", "created": true, "empty": false,
       "publication": {"scope": "local", "accessLog": false}, "hostname": "web.lvh.me",
       "warnings": [], "instancePriority": %s,
       "sources": [%s],
       "routing": {"source": "1a2b", "kind": "static", "waiting": ["web-1:80"],
                   "instances": ["1a2b"]},
       "containerBindings": [
         {"id": "web-1:80", "container": "web-1", "port": 80, "instances": ["web:web-1:80"]}],
       "instances": [
         {"id": "1a2b", "host": "10.1.2.3", "port": 3000, "health": "passing",
          "tags": [], "meta": {}, "checks": [], "outOfRange": false},
         {"id": "web:web-1:80", "host": "172.17.0.2", "port": 80, "health": "passing",
          "tags": [], "meta": {}, "checks": [], "outOfRange": false,
          "containerBinding": "web-1:80"}]}
      """;

  private static final String STATIC_SOURCE =
      """
      {"id": "1a2b", "kind": "static", "instances": ["1a2b"]}\
      """;

  private static final String BOUND_SOURCE =
      """
      {"id": "web-1:80", "kind": "bound", "instances": ["web:web-1:80"]}\
      """;

  private static final String SET_ANSWER =
      """
      {"on": true, "sources": []}
      """;

  @Test
  void priorityPrintsTheSourcesInOrderWithTheirIds() {
    dashboard.answer(
        "GET",
        "/api/workstation/services/web",
        200,
        WEB_WITH_TWO_SOURCES.formatted("true", STATIC_SOURCE + ", " + BOUND_SOURCE));

    Run run = yard("priority", "web");

    assertThat(run.exitCode()).isZero();
    assertThat(run.lines())
        .containsExactly(
            "Instance Priority of web: on. The Route uses the first source with a passing"
                + " Instance.",
            "  1. 1a2b  Static Instance 10.1.2.3:3000  (in use)",
            "  2. web-1:80  Container Binding to container web-1, port 80");
  }

  @Test
  void prioritySetsTheOrderAndPrintsIt() {
    dashboard.answer("PUT", "/api/workstation/services/web/instance-priority", 200, SET_ANSWER);
    dashboard.answer(
        "GET",
        "/api/workstation/services/web",
        200,
        WEB_WITH_TWO_SOURCES.formatted("true", BOUND_SOURCE + ", " + STATIC_SOURCE));

    Run run = yard("priority", "web", "web-1:80", "1a2b");

    assertThat(run.exitCode()).isZero();
    dashboard.verifyCalled(
        "PUT",
        "/api/workstation/services/web/instance-priority",
        """
        {"order": ["web-1:80", "1a2b"]}
        """);
    assertThat(run.lines())
        .element(1)
        .isEqualTo("  1. web-1:80  Container Binding to container web-1, port 80");
  }

  @Test
  void priorityOffAndOnSwitchTheToggle() {
    dashboard.answer("PUT", "/api/workstation/services/web/instance-priority", 200, SET_ANSWER);
    dashboard.answer(
        "GET",
        "/api/workstation/services/web",
        200,
        WEB_WITH_TWO_SOURCES.formatted("false", STATIC_SOURCE + ", " + BOUND_SOURCE));

    Run run = yard("priority", "web", "--off");

    assertThat(run.exitCode()).isZero();
    dashboard.verifyCalled(
        "PUT",
        "/api/workstation/services/web/instance-priority",
        """
        {"on": false}
        """);
    assertThat(run.lines())
        .first()
        .isEqualTo(
            "Instance Priority of web: off. Every passing Instance of every source shares the"
                + " Route.");
    assertThat(yard("priority", "web", "--on", "--off").exitCode())
        .isEqualTo(CommandLine.ExitCode.USAGE);
  }

  @Test
  void priorityWithAnUnknownIdShowsWhyAndExitsWithOne() {
    dashboard.answer(
        "PUT",
        "/api/workstation/services/web/instance-priority",
        400,
        """
        {"status": 400,
         "detail": "web has no Instance Source nothing. Its Instance Sources are: 1a2b."}
        """);

    Run run = yard("priority", "web", "nothing");

    assertThat(run.exitCode()).isEqualTo(1);
    assertThat(run.err()).contains("web has no Instance Source nothing.");
  }

  @Test
  void publishHasNoPriorityOption() {
    Run run = yard("publish", "web", "--priority", "bound,static");

    assertThat(run.exitCode()).isEqualTo(CommandLine.ExitCode.USAGE);
  }

  @Test
  void publishPrintsTheServicesWarnings() {
    dashboard.answer(
        "PUT",
        "/api/workstation/services/web/publication",
        200,
        """
        {"workstation": "ws-a", "name": "web", "state": "healthy", "created": true, "empty": false, "sources": [], "instancePriority": true, "publication": {"scope": "yard", "accessLog": false},
         "hostname": "web.lvh.me", "containerBindings": [], "warnings": [
            {"kind": "unknown-route-profile", "severity": "error",
             "message": "The Route Profile team-login does not exist."},
            {"kind": "loopback-instance", "severity": "risk",
             "message": "The Instance at 127.0.0.1:8080 is on a loopback address."}],
         "instances": [
            {"id": "7b1c", "host": "127.0.0.1", "port": 8080, "health": "passing",
             "tags": [], "meta": {}, "checks": [], "outOfRange": false}]}
        """);

    Run run = yard("publish", "web", "--scope", "yard");

    assertThat(run.exitCode()).isZero();
    assertThat(run.err())
        .isEqualTo(
            "error: The Route Profile team-login does not exist.\n"
                + "risk: The Instance at 127.0.0.1:8080 is on a loopback address.\n");
  }

  @Test
  void publishWithAccessLogOnSendsTrueAndSaysSo() {
    dashboard.answer(
        "PUT",
        "/api/workstation/services/web/publication",
        200,
        """
        {"workstation": "ws-a", "name": "web", "state": "healthy", "created": true, "empty": false, "sources": [], "instancePriority": true, "publication": {"scope": "local", "accessLog": true},
         "hostname": "web.lvh.me", "containerBindings": [], "warnings": [], "instances": []}
        """);

    Run run = yard("publish", "web", "--access-log", "on");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out()).contains("Access log: on.");
    dashboard.verifyCalled(
        "PUT",
        "/api/workstation/services/web/publication",
        """
        {"accessLog": true}
        """);
  }

  @Test
  void publishRefusesAnUnknownAccessLogValue() {
    Run run = yard("publish", "web", "--access-log", "maybe");

    assertThat(run.exitCode()).isEqualTo(CommandLine.ExitCode.USAGE);
    assertThat(run.err()).contains("on or off");
  }

  @Test
  void publishRefusesAnUnknownScope() {
    Run run = yard("publish", "web", "--scope", "world");

    assertThat(run.exitCode()).isEqualTo(CommandLine.ExitCode.USAGE);
    assertThat(run.err()).contains("local").contains("yard");
  }

  @Test
  void unpublishRemovesThePublication() {
    dashboard.answer("DELETE", "/api/workstation/services/web/publication", 204, null);

    Run run = yard("unpublish", "web");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out()).isEqualTo("Unpublished web. Its Instances stay registered.\n");
    dashboard.verifyCalled("DELETE", "/api/workstation/services/web/publication");
  }

  @Test
  void publishingUnknownServiceIsRefused() {
    dashboard.answer(
        "PUT",
        "/api/workstation/services/wbe/publication",
        404,
        """
        {"type": "about:blank", "title": "Not Found", "status": 404,
         "detail": "This Workstation has no Service wbe."}
        """);

    Run run = yard("publish", "wbe");

    assertThat(run.exitCode()).isNotZero();
    assertThat(run.err()).isEqualTo("This Workstation has no Service wbe.\n");
  }
}
