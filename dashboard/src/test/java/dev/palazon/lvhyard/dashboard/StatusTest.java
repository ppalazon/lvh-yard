package dev.palazon.lvhyard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributes;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/** The status of this Workstation's LVH Yard: its API, its page and their parts. */
class StatusTest extends DashboardTest {

  @Test
  void consulWithALeaderIsOk() {
    ResponseEntity<JsonNode> response = get("/api/status");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode consul = part(response.getBody(), "Consul");
    assertThat(consul.get("state").asString()).isEqualTo("ok");
    assertThat(detail(consul, "leader").get("value").asString()).endsWith(":8300");
  }

  @Test
  void consulShowsEachWorkstationOfItsWanPoolAndWhetherItIsAlive() {
    JsonNode consul = part(get("/api/status").getBody(), "Consul");

    assertThat(consul.get("state").asString()).isEqualTo("ok");
    JsonNode remoteMember = detail(consul, RemoteWorkstation.NAME);
    assertThat(remoteMember.get("value").asString()).startsWith("alive at ");
    assertThat(remoteMember.get("state").asString()).isEqualTo("ok");
    assertThat(detail(consul, WORKSTATION).get("value").asString())
        .isEqualTo("alive at " + lanAddress() + ":8302");
  }

  @Test
  void aPeerMissingFromTheWanPoolIsAWarning() {
    String joined = lanAddress() + ":8302";
    String missing = "192.0.2.1";

    remote.whileDashboardRunsWith(
        () -> {
          JsonNode status = remote.status();
          JsonNode consul = part(status, "Consul");
          assertThat(consul.get("state").asString()).isEqualTo("warning");
          assertThat(consul.get("reason").asString()).contains(missing);
          assertThat(detail(consul, "peer " + missing).get("state").asString())
              .isEqualTo("warning");
          assertThat(consul.get("details").toString()).doesNotContain("peer " + joined);
          assertThat(status.get("overall").asString()).isEqualTo("warning");
        },
        "--lvh-yard.consul.peers=" + joined + " " + missing);
  }

  @Test
  void dockerThatAnswersIsOkWithItsVersion() {
    JsonNode docker = part(get("/api/status").getBody(), "Docker");

    assertThat(docker.get("state").asString()).isEqualTo("ok");
    assertThat(detail(docker, "version").get("value").asString()).matches("\\d+\\.\\d+.*");
  }

  @Test
  void aMissingDockerSocketIsFailingAndSaysSo() {
    remote.whileDashboardRunsWith(
        () -> {
          JsonNode status = remote.status();
          JsonNode docker = part(status, "Docker");
          assertThat(docker.get("state").asString()).isEqualTo("failing");
          assertThat(docker.get("reason").asString())
              .contains("/nowhere/docker.sock")
              .contains("no Docker socket");
          assertThat(status.get("overall").asString()).isEqualTo("failing");
        },
        "--lvh-yard.docker.host=unix:///nowhere/docker.sock");
  }

  @Test
  void aDockerSocketThatCannotBeReadIsFailingAndNamesItsGroup() throws IOException {
    Path socket = Files.createTempFile("docker", ".sock");
    try {
      Files.setPosixFilePermissions(socket, Set.of());
      String group = Files.readAttributes(socket, PosixFileAttributes.class).group().getName();

      remote.whileDashboardRunsWith(
          () -> {
            JsonNode docker = part(remote.status(), "Docker");
            assertThat(docker.get("state").asString()).isEqualTo("failing");
            assertThat(docker.get("reason").asString())
                .contains("cannot read " + socket)
                .contains("group " + group);
          },
          "--lvh-yard.docker.host=unix://" + socket);
    } finally {
      Files.delete(socket);
    }
  }

  @Test
  void pageShowsEachPartWithItsStateAndReason() {
    ResponseEntity<String> response = getPage("/status");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    String consul = div(response.getBody(), "id=\"part-consul\"");
    assertThat(consul)
        .contains("Consul")
        .contains("OK")
        .contains("Consul has a leader")
        .contains(RemoteWorkstation.NAME);
    assertThat(div(response.getBody(), "id=\"part-docker\"")).contains("Docker").contains("OK");
  }

  @Test
  void pageShowsAFailingPartAsFailing() {
    remote.whileDashboardRunsWith(
        () ->
            assertThat(remote.page("/status"))
                .contains("Failing")
                .contains("There is no Docker socket at /nowhere/docker.sock"),
        "--lvh-yard.docker.host=unix:///nowhere/docker.sock");
  }

  @Test
  void pageRefreshesItsPartsEveryFiveSeconds() {
    assertThat(openingTag(getPage("/status").getBody(), "id=\"status\""))
        .contains("hx-get=\"/status\"")
        .contains("hx-trigger=\"every 5s\"")
        .contains("hx-select=\"#status\"");
  }

  @Test
  void summaryShowsADotForEachPartWithItsNameStateAndReason() {
    ResponseEntity<String> response = getPage("/status/summary");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    String consul = openingTag(response.getBody(), "id=\"dot-consul\"");
    assertThat(consul)
        .contains("aria-label=\"Consul: OK. Consul has a leader")
        .contains("title=\"Consul: OK. Consul has a leader");
    assertThat(response.getBody())
        .contains("id=\"dot-docker\"")
        .contains("✓")
        .doesNotContain("<html");
  }

  @Test
  void summaryShowsAFailingPartWithACrossAndLinksToTheStatusPage() {
    remote.whileDashboardRunsWith(
        () -> {
          String summary = remote.page("/status/summary");
          assertThat(summary).contains("href=\"/status\"");
          assertThat(openingTag(summary, "id=\"dot-docker\""))
              .contains("aria-label=\"Docker: Failing. There is no Docker socket at");
          assertThat(summary).contains("✕");
        },
        "--lvh-yard.docker.host=unix:///nowhere/docker.sock");
  }

  @Test
  void everyPageHeaderLoadsTheDotsEveryFiveSecondsAndKeepsThemOnAnError() {
    for (String path : new String[] {"/", "/status", "/register"}) {
      assertThat(openingTag(getPage(path).getBody(), "id=\"status-dots\""))
          .as(path)
          .contains("hx-get=\"/status/summary\"")
          .contains("hx-trigger=\"load, every 5s\"")
          .contains("hx-status:4xx=\"swap:none\"")
          .contains("hx-status:5xx=\"swap:none\"");
    }
  }

  /** The part called {@code name} of a status. */
  static JsonNode part(JsonNode status, String name) {
    return status
        .get("parts")
        .valueStream()
        .filter(part -> part.get("name").asString().equals(name))
        .findFirst()
        .orElseThrow(() -> new AssertionError("No part " + name + " in " + status));
  }

  /** The detail called {@code name} of a part. */
  static JsonNode detail(JsonNode part, String name) {
    return part.get("details")
        .valueStream()
        .filter(detail -> detail.get("name").asString().equals(name))
        .findFirst()
        .orElseThrow(() -> new AssertionError("No detail " + name + " in " + part));
  }
}
