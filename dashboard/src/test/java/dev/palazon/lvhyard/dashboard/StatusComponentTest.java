package dev.palazon.lvhyard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/** The status of this Workstation's LVH Yard: its API, its page and their parts. */
class StatusComponentTest extends DashboardComponentTest {

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
  void dockerThatAnswersItsPingIsOkWithItsVersion() {
    JsonNode docker = part(get("/api/status").getBody(), "Docker");

    assertThat(docker.get("state").asString()).isEqualTo("ok");
    assertThat(detail(docker, "ping").get("state").asString()).isEqualTo("ok");
    assertThat(detail(docker, "version").get("value").asString()).matches("\\d+\\.\\d+.*");
  }

  @Test
  void statusHasConsulTraefikAndDockerOnly() {
    JsonNode status = get("/api/status").getBody();

    assertThat(status.get("parts").valueStream().map(part -> part.get("name").asString()))
        .containsExactly("Consul", "Traefik", "Docker");
  }

  @Test
  void pageShowsEachPartWithItsStateAndReason() {
    ResponseEntity<String> response = getPage("/status");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    String consul = div(response.getBody(), "id=\"part-consul\"");
    assertThat(consul)
        .contains("Consul")
        .contains(badge("success", "✓ OK"))
        .contains("Consul has a leader")
        .contains(RemoteWorkstation.NAME);
    assertThat(div(response.getBody(), "id=\"part-docker\""))
        .contains("Docker")
        .contains(badge("success", "✓ OK"));
    assertThat(div(response.getBody(), "id=\"part-traefik\""))
        .contains("Traefik")
        .contains("ping")
        .contains("last fetch");
    assertThat(response.getBody())
        .contains("Whether LVH Yard works on " + WORKSTATION + ".")
        .doesNotContain("Core Services");
  }

  @Test
  void pageRefreshesItsPartsWithSseAndFallback() {
    assertThat(openingTag(getPage("/status").getBody(), "id=\"status\""))
        .contains("hx-get=\"/status\"")
        .contains("hx-trigger=\"status-changed from:body, every 30s\"")
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
        .contains("id=\"dot-traefik\"")
        .contains("id=\"dot-docker\"")
        .contains("✓")
        .doesNotContain("<html");
    assertThat(response.getBody().split("id=\"dot-", -1)).hasSize(4);
  }

  @Test
  void everyPageHeaderLoadsTheDotsWithSseAndKeepsThemOnAnError() {
    for (String path : new String[] {"/", "/status", "/static-instances"}) {
      assertThat(openingTag(getPage(path).getBody(), "id=\"status-dots\""))
          .as(path)
          .contains("hx-get=\"/status/summary\"")
          .contains("hx-trigger=\"load, status-changed from:body, every 30s\"")
          .contains("hx-status:4xx=\"swap:none\"")
          .contains("hx-status:5xx=\"swap:none\"");
    }
  }
}
