package dev.palazon.lvhyard.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.Test;

class StatusTest extends YardTestSupport {

  @Test
  void statusPrintsEachPartWithItsReasonAndDetailsAndSucceedsWhenAllAreOk() {
    dashboard.answer(
        "GET",
        "/api/status",
        200,
        """
        {"overall": "ok", "parts": [
          {"name": "Consul", "state": "ok", "reason": "Consul has a leader, and 2 Workstations are in its WAN pool.",
           "details": [{"name": "leader", "value": "10.0.0.1:8300"},
                       {"name": "ws-b", "value": "alive at 10.0.0.2:8302", "state": "ok"}]},
          {"name": "Docker", "state": "ok", "reason": "Docker answers, so Container Bindings work.",
           "details": [{"name": "version", "value": "28.1.1"}]}]}
        """);

    Run run = yard("status");

    assertThat(run.exitCode()).isZero();
    assertThat(run.lines())
        .containsExactly(
            "LVH Yard on this Workstation: ok",
            "  ok    Consul: Consul has a leader, and 2 Workstations are in its WAN pool.",
            "          leader: 10.0.0.1:8300",
            "          ws-b: alive at 10.0.0.2:8302",
            "  ok    Docker: Docker answers, so Container Bindings work.",
            "          version: 28.1.1");
  }

  @Test
  void statusShowsTraefikWithItsDetailsAndSucceedsOnAWarning() {
    dashboard.answer(
        "GET",
        "/api/status",
        200,
        """
        {"overall": "warning", "parts": [
          {"name": "Consul", "state": "ok", "reason": "Consul has a leader.", "details": []},
          {"name": "Traefik", "state": "warning",
           "reason": "Traefik answers, but last fetched its Routes from this dashboard 40 s ago, more than 15 s.",
           "details": [{"name": "ping", "value": "OK at http://127.0.0.1:8080/ping", "state": "ok"},
                       {"name": "last fetch", "value": "40 s ago", "state": "warning"}]},
          {"name": "Docker", "state": "ok", "reason": "Docker answers, so Container Bindings work.", "details": []}]}
        """);

    Run run = yard("status");

    assertThat(run.exitCode()).isZero();
    assertThat(run.lines())
        .containsExactly(
            "LVH Yard on this Workstation: warning",
            "  ok    Consul: Consul has a leader.",
            "  WARN  Traefik: Traefik answers, but last fetched its Routes from this dashboard 40 s"
                + " ago, more than 15 s.",
            "          ping: OK at http://127.0.0.1:8080/ping",
            "          last fetch: 40 s ago (warning)",
            "  ok    Docker: Docker answers, so Container Bindings work.");
  }

  @Test
  void statusFailsWhenAnyPartIsFailing() {
    dashboard.answer(
        "GET",
        "/api/status",
        200,
        """
        {"overall": "failing", "parts": [
          {"name": "Consul", "state": "ok", "reason": "Consul has a leader.", "details": []},
          {"name": "Docker", "state": "failing",
           "reason": "No Docker socket at /var/run/docker.sock. Start Docker, or fix DOCKER_HOST.", "details": []}]}
        """);

    Run run = yard("status");

    assertThat(run.exitCode()).isEqualTo(1);
    assertThat(run.lines())
        .containsExactly(
            "LVH Yard on this Workstation: failing",
            "  ok    Consul: Consul has a leader.",
            "  FAIL  Docker: No Docker socket at /var/run/docker.sock. Start Docker, or fix"
                + " DOCKER_HOST.");
  }

  @Test
  void statusWithWarningsOnlySucceedsUnlessStrict() {
    dashboard.answer(
        "GET",
        "/api/status",
        200,
        """
        {"overall": "warning", "parts": [
          {"name": "Consul", "state": "warning", "reason": "A Peer is not in Consul's WAN pool: 10.0.0.9.",
           "details": [{"name": "peer 10.0.0.9", "value": "not in the WAN pool", "state": "warning"}]}]}
        """);

    Run lenient = yard("status");
    Run strict = yard("status", "--strict");

    assertThat(lenient.exitCode()).isZero();
    assertThat(lenient.lines())
        .containsExactly(
            "LVH Yard on this Workstation: warning",
            "  WARN  Consul: A Peer is not in Consul's WAN pool: 10.0.0.9.",
            "          peer 10.0.0.9: not in the WAN pool (warning)");
    assertThat(strict.exitCode()).isEqualTo(1);
  }

  @Test
  void statusSaysWhenTheDashboardDoesNotAnswer() throws IOException {
    String nothingListens = "http://127.0.0.1:" + freePort();

    Run run = yardAsTyped("--dashboard", nothingListens, "status");

    assertThat(run.exitCode()).isEqualTo(1);
    assertThat(run.err()).contains("The dashboard does not answer at").contains(nothingListens);
  }
}
