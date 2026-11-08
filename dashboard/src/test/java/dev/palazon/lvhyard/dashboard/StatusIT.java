package dev.palazon.lvhyard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributes;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import tools.jackson.databind.JsonNode;

/** The status of this Workstation's LVH Yard: its API, its page and their parts. */
class StatusIT extends DashboardIT {

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

  @Test
  void infrastructureTransitionEmitsStatusChangedOverEventsStream() throws Exception {
    try (EventStreamClient client = new EventStreamClient(port)) {
      GenericContainer<?> traefik = TestcontainersConfiguration.traefik(port);
      traefik.start();
      try {
        await()
            .atMost(Duration.ofSeconds(15))
            .until(() -> client.lines.contains("event:status-changed"));

        String summary = getPage("/status/summary").getBody();
        assertThat(openingTag(summary, "id=\"dot-traefik\"")).contains("aria-label=\"Traefik: OK.");

        String statusPage = getPage("/status").getBody();
        assertThat(div(statusPage, "id=\"part-traefik\"")).contains(badge("success", "✓ OK"));
      } finally {
        traefik.stop();
      }
    }
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
        },
        "--lvh-yard.consul.peers=" + joined + " " + missing);
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
              .contains("No Docker socket");
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
  void pageShowsAFailingPartAsFailing() {
    remote.whileDashboardRunsWith(
        () ->
            assertThat(remote.page("/status"))
                .contains(badge("destructive", "✕ Failing"))
                .contains("No Docker socket at /nowhere/docker.sock"),
        "--lvh-yard.docker.host=unix:///nowhere/docker.sock");
  }

  @Test
  void pageAndSummaryShowAPartWithAWarningAsAWarning() {
    remote.whileDashboardRunsWith(
        () -> {
          String consul = div(remote.page("/status"), "id=\"part-consul\"");
          assertThat(consul).contains("! Warning").contains("A Peer is not in Consul");
          String summary = remote.page("/status/summary");
          assertThat(openingTag(summary, "id=\"dot-consul\""))
              .contains("aria-label=\"Consul: Warning. A Peer is not in Consul");
          assertThat(summary).containsPattern("id=\"dot-consul\"[^>]*>!</span>");
        },
        "--lvh-yard.consul.peers=192.0.2.1");
  }

  @Test
  void summaryShowsAFailingPartWithACrossAndLinksToTheStatusPage() {
    remote.whileDashboardRunsWith(
        () -> {
          String summary = remote.page("/status/summary");
          assertThat(summary).contains("href=\"/status\"");
          assertThat(openingTag(summary, "id=\"dot-docker\""))
              .contains("aria-label=\"Docker: Failing. No Docker socket at");
          assertThat(summary).contains("✕");
        },
        "--lvh-yard.docker.host=unix:///nowhere/docker.sock");
  }
}
