package dev.palazon.lvhyard.cli;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TestReachabilityTest extends YardTestSupport {

  @Test
  void testPrintsEachCheckOfAnOwnServiceAndSucceedsWhenAllPass() {
    dashboard.answer(
        "GET",
        "/api/workstation/services/web/reachability",
        200,
        """
        {"workstation": "ws-a", "service": "web", "passed": true,
         "instances": [{"id": "7b1c", "address": "10.1.2.3:8080", "reached": true}],
         "routes": [{"hostname": "web.lvh.me", "reached": true, "status": 200}]}
        """);

    Run run = yard("test", "web");

    assertThat(run.exitCode()).isZero();
    assertThat(run.lines())
        .containsExactly(
            "Reachability of web on ws-a, from this Workstation:",
            "  ok    Instance 10.1.2.3:8080",
            "  ok    Route web.lvh.me: answered 200",
            "Every check passed.");
  }

  @Test
  void testFailsWhenAnyCheckFailsAndSaysWhy() {
    dashboard.answer(
        "GET",
        "/api/workstation/services/web/reachability",
        200,
        """
        {"workstation": "ws-a", "service": "web", "passed": false,
         "instances": [{"id": "7b1c", "address": "10.1.2.3:8080", "reached": true},
                       {"id": "8c2d", "address": "10.1.2.3:8081", "reached": false,
                        "error": "Connection refused"}],
         "routes": [{"hostname": "web.lvh.me", "reached": false, "status": 502,
                     "error": "Traefik answered 502: no Instance answered."}]}
        """);

    Run run = yard("test", "web");

    assertThat(run.exitCode()).isEqualTo(1);
    assertThat(run.lines())
        .containsExactly(
            "Reachability of web on ws-a, from this Workstation:",
            "  ok    Instance 10.1.2.3:8080",
            "  FAIL  Instance 10.1.2.3:8081: Connection refused",
            "  FAIL  Route web.lvh.me: Traefik answered 502: no Instance answered.",
            "2 of 3 checks failed.");
  }

  @Test
  void testOfARemoteServiceWithoutARouteSaysSo() {
    dashboard.answer(
        "GET",
        "/api/services/ws-b/grafana/reachability",
        200,
        """
        {"workstation": "ws-b", "service": "grafana", "passed": true,
         "instances": [{"id": "1a2b", "address": "192.168.1.20:3000", "reached": true}], "routes": [],
         "noRoute": "Published with Local Scope: only ws-b routes it, so only its Instances are tested from here."}
        """);

    Run run = yard("test", "grafana", "--workstation", "ws-b");

    assertThat(run.exitCode()).isZero();
    assertThat(run.lines())
        .containsExactly(
            "Reachability of grafana on ws-b, from this Workstation:",
            "  ok    Instance 192.168.1.20:3000",
            "Published with Local Scope: only ws-b routes it, so only its Instances are tested from"
                + " here.",
            "Every check passed.");
  }

  @Test
  void testSkipsAnInstanceOutOfRangeWithoutFailing() {
    dashboard.answer(
        "GET",
        "/api/services/ws-b/grafana/reachability",
        200,
        """
        {"workstation": "ws-b", "service": "grafana", "passed": true,
         "instances": [{"id": "1a2b", "address": "192.168.1.20:3000", "reached": true},
                       {"id": "3c4d", "address": "127.0.0.1:3000", "reached": false, "outOfRange": true}],
         "routes": [{"hostname": "grafana.ws-b.lvh.me", "reached": true, "status": 200}]}
        """);

    Run run = yard("test", "grafana", "--workstation", "ws-b");

    assertThat(run.exitCode()).isZero();
    assertThat(run.lines())
        .containsExactly(
            "Reachability of grafana on ws-b, from this Workstation:",
            "  ok    Instance 192.168.1.20:3000",
            "  skip  Instance 127.0.0.1:3000: out of range",
            "  ok    Route grafana.ws-b.lvh.me: answered 200",
            "Every check passed. 1 skipped.");
  }

  @Test
  void testMarksTheInstancesTheRouteUsesNow() {
    dashboard.answer(
        "GET",
        "/api/workstation/services/web/reachability",
        200,
        """
        {"workstation": "ws-a", "service": "web", "passed": true,
         "instances": [{"id": "7b1c", "address": "10.1.2.3:8080", "reached": true, "routed": true},
                       {"id": "8c2d", "address": "172.18.0.5:80", "reached": true}],
         "routes": [{"hostname": "web.lvh.me", "reached": true, "status": 200}],
         "routing": {"waiting": ["8c2d"], "source": "7b1c", "kind": "static",
                     "instances": ["7b1c"]}}
        """);

    Run run = yard("test", "web");

    assertThat(run.exitCode()).isZero();
    assertThat(run.lines())
        .containsExactly(
            "Reachability of web on ws-a, from this Workstation:",
            "  ok    Instance 10.1.2.3:8080 (in the Route)",
            "  ok    Instance 172.18.0.5:80 (not in the Route now)",
            "  ok    Route web.lvh.me: answered 200",
            "The Route uses the static Instance Source 7b1c now.",
            "Every check passed.");
  }

  @Test
  void testSaysWhenTheInstancePriorityIsOff() {
    dashboard.answer(
        "GET",
        "/api/workstation/services/web/reachability",
        200,
        """
        {"workstation": "ws-a", "service": "web", "passed": true,
         "instances": [{"id": "7b1c", "address": "10.1.2.3:8080", "reached": true, "routed": true},
                       {"id": "8c2d", "address": "172.18.0.5:80", "reached": true, "routed": true}],
         "routes": [{"hostname": "web.lvh.me", "reached": true, "status": 200}],
         "routing": {"waiting": [], "off": true, "instances": ["7b1c", "8c2d"]}}
        """);

    Run run = yard("test", "web");

    assertThat(run.exitCode()).isZero();
    assertThat(run.lines())
        .containsExactly(
            "Reachability of web on ws-a, from this Workstation:",
            "  ok    Instance 10.1.2.3:8080 (in the Route)",
            "  ok    Instance 172.18.0.5:80 (in the Route)",
            "  ok    Route web.lvh.me: answered 200",
            "The Instance Priority is off, so the Route uses every passing Instance,"
                + " or every Instance in range if none passes.",
            "Every check passed.");
  }

  @Test
  void testSaysWhenTheRouteUsesTheAlias() {
    dashboard.answer(
        "GET",
        "/api/workstation/services/web/reachability",
        200,
        """
        {"workstation": "ws-a", "service": "web", "passed": false,
         "instances": [{"id": "7b1c", "address": "10.1.2.3:8080", "reached": false,
                        "error": "Connection refused"}],
         "routes": [{"hostname": "web.lvh.me", "reached": true, "status": 200}],
         "routing": {"waiting": [], "kind": "alias", "instances": ["9e3f"]},
         "alias": {"id": "ws-b/web", "workstation": "ws-b", "service": "web",
                   "state": "active"}}
        """);

    Run run = yard("test", "web");

    assertThat(run.exitCode()).isEqualTo(1);
    assertThat(run.lines())
        .containsExactly(
            "Reachability of web on ws-a, from this Workstation:",
            "  FAIL  Instance 10.1.2.3:8080 (not in the Route now): Connection refused",
            "  ok    Route web.lvh.me: answered 200",
            "The Route sends requests to the Alias's Remote Service: web on ws-b.",
            "1 of 2 checks failed.");
  }

  @Test
  void testOfUnknownServiceIsRefused() {
    dashboard.answer(
        "GET",
        "/api/workstation/services/wbe/reachability",
        404,
        """
        {"type": "about:blank", "title": "Not Found", "status": 404,
         "detail": "This Workstation has no Service wbe."}
        """);

    Run run = yard("test", "wbe");

    assertThat(run.exitCode()).isEqualTo(1);
    assertThat(run.err()).isEqualTo("This Workstation has no Service wbe.\n");
  }
}
