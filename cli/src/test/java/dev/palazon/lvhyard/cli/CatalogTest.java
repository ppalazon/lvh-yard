package dev.palazon.lvhyard.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class CatalogTest extends YardTestSupport {

  @Test
  void catalogGroupsServicesInAlignedColumnsWithoutInstanceIdsOrWarnings() {
    catalogAnswer();

    Run run = yard("catalog");

    assertThat(run.exitCode()).isZero();
    assertThat(run.lines())
        .containsExactly(
            "ws-a (this Workstation)",
            "  web       degraded     web.lvh.me  1 of 3 passing",
            "  worker    unpublished              0 of 2 passing",
            "ws-b",
            "  database  healthy      db.lvh.me   2 of 2 passing",
            "  cache     unavailable              Empty",
            "ws-c: Workstation unreachable",
            "ws-d: no Services");
  }

  @Test
  void catalogFiltersWorkstationsAndStatesThroughTheYardApi() {
    catalogAnswer();

    Run run = yard("catalog", "--workstation", "ws-a", "--state", "degraded");

    assertThat(run.exitCode()).isZero();
    assertThat(run.lines())
        .containsExactly("ws-a (this Workstation)", "  web  degraded  web.lvh.me  1 of 3 passing");
  }

  @Test
  void catalogStateFilterKeepsUnreachableWorkstations() {
    catalogAnswer();

    Run run = yard("catalog", "--state", "unpublished");

    assertThat(run.exitCode()).isZero();
    assertThat(run.lines())
        .containsExactly(
            "ws-a (this Workstation)",
            "  worker  unpublished     0 of 2 passing",
            "ws-b: no Services",
            "ws-c: Workstation unreachable",
            "ws-d: no Services");
  }

  @Test
  void catalogWorkstationFilterKeepsAnUnreachableWorkstation() {
    catalogAnswer();

    Run run = yard("catalog", "--workstation", "ws-c");

    assertThat(run.exitCode()).isZero();
    assertThat(run.lines()).containsExactly("ws-c: Workstation unreachable");
  }

  @Test
  void catalogRejectsUnknownStatesAndListsEveryState() {
    Run run = yard("catalog", "--state", "broken");

    assertThat(run.exitCode()).isEqualTo(2);
    assertThat(run.err())
        .contains("broken", "unpublished", "unavailable", "unhealthy", "degraded", "healthy");
  }

  @Test
  void catalogDetailsShowsWorkstationsWithServiceStateHostnameInstancesAndHealth() {
    dashboard.answer(
        "GET",
        "/api/yard",
        200,
        """
        {"home": {"name": "ws-a", "services": [
           {"workstation": "ws-a", "name": "api", "state": "unavailable", "created": true, "empty": false, "sources": [], "instancePriority": true, "publication": {"scope": "local", "accessLog": false},
            "hostname": "api.lvh.me", "containerBindings": [], "warnings": [], "instances": []},
           {"workstation": "ws-a", "name": "grafana", "state": "healthy", "created": true, "empty": false, "sources": [], "instancePriority": true, "publication": {"scope": "local", "accessLog": false},
            "hostname": "grafana.lvh.me", "containerBindings": [], "warnings": [], "instances": [
              {"id": "3f2a", "host": "10.1.2.3", "port": 3000, "health": "passing",
               "tags": [], "meta": {}, "checks": [], "outOfRange": false}]},
           {"workstation": "ws-a", "name": "web", "state": "unpublished", "created": true, "empty": false, "sources": [], "instancePriority": true, "containerBindings": [],
            "warnings": [], "instances": [
              {"id": "7b1c", "host": "10.1.2.3", "port": 8080, "health": "critical",
               "tags": [], "meta": {}, "checks": [], "outOfRange": false},
              {"id": "9d4e", "host": "10.1.2.3", "port": 8081, "health": "warning",
               "tags": [], "meta": {}, "checks": [], "outOfRange": false}]}]},
         "remotes": [
           {"name": "ws-b", "unreachable": false, "services": [
             {"workstation": "ws-b", "name": "postgres", "state": "unhealthy", "created": true, "empty": false, "sources": [], "instancePriority": true,
              "publication": {"scope": "local", "accessLog": false}, "containerBindings": [], "warnings": [], "instances": [
                {"id": "5e6f", "host": "10.1.2.4", "port": 5432, "health": "critical",
                 "tags": [], "meta": {}, "checks": [], "outOfRange": false}]}]},
           {"name": "ws-c", "unreachable": true, "services": []},
           {"name": "ws-d", "unreachable": false, "services": []}]}
        """);

    Run run = yard("catalog", "--details");

    assertThat(run.exitCode()).isZero();
    assertThat(run.lines())
        .containsExactly(
            "ws-a (this Workstation)",
            "  api: unavailable at api.lvh.me",
            "  grafana: healthy at grafana.lvh.me",
            "    10.1.2.3:3000  passing   3f2a",
            "  web: unpublished",
            "    10.1.2.3:8080  critical  7b1c",
            "    10.1.2.3:8081  warning   9d4e",
            "ws-b",
            "  postgres: unhealthy",
            "    10.1.2.4:5432  critical  5e6f",
            "ws-c: Workstation unreachable",
            "ws-d: no Services");
  }

  @Test
  void catalogDetailsFiltersServicesAndKeepsInstanceIdsHealthAndWarnings() {
    catalogAnswer();

    Run run = yard("catalog", "--details", "--workstation", "ws-a", "--state", "degraded");

    assertThat(run.exitCode()).isZero();
    assertThat(run.lines())
        .containsExactly(
            "ws-a (this Workstation)",
            "  web: degraded at web.lvh.me",
            "    10.1.2.3:8080  passing   hidden-good  out of range",
            "    127.0.0.1:8081  critical  hidden-bad",
            "    10.1.2.3:8082  warning   hidden-warning",
            "    risk: Hidden warning.",
            "    error: Missing Route Profile.");
  }

  @Test
  void catalogDetailsMarksOnlyTheInstancesThatTheRouteUses() {
    dashboard.answer(
        "GET",
        "/api/yard",
        200,
        """
        {"home": {"name": "ws-a", "services": [
          {"workstation": "ws-a", "name": "web", "state": "healthy", "created": true, "empty": false, "sources": [], "instancePriority": true,
           "hostname": "web.lvh.me", "containerBindings": [], "warnings": [],
           "routing": {"waiting": [], "kind": "static", "instances": ["1a2b"]},
           "instances": [
             {"id": "1a2b", "host": "10.1.2.3", "port": 3000, "health": "passing",
              "tags": [], "meta": {}, "checks": [], "outOfRange": false},
             {"id": "web:abc:80", "host": "172.17.0.2", "port": 80, "health": "passing",
              "tags": [], "meta": {}, "checks": [], "outOfRange": false,
              "containerBinding": "abc:80"}]}]}, "remotes": []}
        """);

    Run run = yard("catalog", "--details");

    assertThat(run.exitCode()).isZero();
    assertThat(run.lines())
        .containsExactly(
            "ws-a (this Workstation)",
            "  web: healthy at web.lvh.me",
            "    10.1.2.3:3000  passing   1a2b  routed",
            "    172.17.0.2:80  passing   web:abc:80");
  }

  @Test
  void catalogDetailsCanSelectARemoteWorkstation() {
    catalogAnswer();

    Run run = yard("catalog", "--details", "--workstation", "ws-b");

    assertThat(run.exitCode()).isZero();
    assertThat(run.lines())
        .containsExactly(
            "ws-b",
            "  database: healthy at db.lvh.me",
            "    10.1.2.4:5432  passing   db-first",
            "    10.1.2.4:5433  passing   db-second",
            "  cache: unavailable");
  }

  @Test
  void catalogDetailsKeepsUnreachableAndEmptyWorkstationsWhenFilteringByState() {
    catalogAnswer();

    Run run = yard("catalog", "--details", "--state", "unpublished");

    assertThat(run.exitCode()).isZero();
    assertThat(run.lines())
        .containsExactly(
            "ws-a (this Workstation)",
            "  worker: unpublished",
            "    10.1.2.3:8083  critical  worker-critical",
            "    10.1.2.3:8084  warning   worker-warning",
            "ws-b: no Services",
            "ws-c: Workstation unreachable",
            "ws-d: no Services");
  }

  @Test
  void catalogRejectsUnknownWorkstationsInBothViews() {
    catalogAnswer();

    for (String[] args :
        List.of(
            new String[] {"catalog", "--workstation", "ws-typo"},
            new String[] {"catalog", "--details", "--workstation", "ws-typo"})) {
      Run run = yard(args);

      assertThat(run.exitCode()).isNotZero();
      assertThat(run.out()).isEmpty();
      assertThat(run.err()).contains("The Yard has no Workstation ws-typo.");
    }
  }
}
