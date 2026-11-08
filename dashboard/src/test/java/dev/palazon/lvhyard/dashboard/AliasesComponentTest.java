package dev.palazon.lvhyard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/** Aliases: short hostnames this Workstation gives to Remote Services, for its own use only. */
class AliasesComponentTest extends DashboardComponentTest {

  /** The Alias called {@code name}, as the list of this Workstation's Aliases has it. */
  private JsonNode alias(String name) {
    return get(aliasesPath())
        .getBody()
        .valueStream()
        .filter(alias -> alias.get("name").asString().equals(name))
        .findFirst()
        .orElseThrow(() -> new AssertionError("No Alias " + name));
  }

  static String aliasesPath() {
    return "/api/workstation/aliases";
  }

  static String aliasPath(String alias) {
    return "/api/workstation/aliases/" + alias;
  }

  static List<String> routedHostnames(JsonNode http) {
    return TraefikConfigurationIT.routedHostnames(http);
  }

  @Test
  void aliasNamesARemoteWorkstationAndAServiceByTheirDnsLabels() {
    String web = newServiceName("web");
    register(web, Map.of("host", "10.1.2.3", "port", 8080));
    String alias = newServiceName("alias");

    ResponseEntity<JsonNode> own =
        put(aliasPath(alias), Map.of("workstation", WORKSTATION, "service", web));
    ResponseEntity<JsonNode> badWorkstation =
        put(aliasPath(alias), Map.of("workstation", "WS_B", "service", web));
    ResponseEntity<JsonNode> badService =
        put(aliasPath(alias), Map.of("workstation", RemoteWorkstation.NAME, "service", "-web"));

    assertThat(own.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(own.getBody().get("detail").asString()).contains("Remote Service");
    assertThat(badWorkstation.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(badWorkstation.getBody().get("detail").asString()).contains("WS_B");
    assertThat(badService.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(badService.getBody().get("detail").asString()).contains("-web");
    assertThat(
            get(aliasesPath()).getBody().valueStream().map(found -> found.get("name").asString()))
        .doesNotContain(alias);
  }

  @Test
  void aliasOfAWorkstationOrServiceNotInTheYardIsKeptWithoutARoute() {
    String nowhere = newServiceName("nowhere");
    String nothing = newServiceName("nothing");

    ResponseEntity<JsonNode> noWorkstation =
        put(aliasPath(nowhere), Map.of("workstation", "ws-nowhere", "service", nowhere));
    ResponseEntity<JsonNode> noService =
        put(aliasPath(nothing), Map.of("workstation", RemoteWorkstation.NAME, "service", nothing));

    assertThat(noWorkstation.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(noWorkstation.getBody().get("state").asString()).isEqualTo("missing");
    assertThat(noWorkstation.getBody().get("reason").asString()).contains("ws-nowhere");
    assertThat(noService.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(noService.getBody().get("state").asString()).isEqualTo("missing");
    assertThat(routedHostnames(get("/traefik/configuration").getBody().get("http")))
        .doesNotContain(nowhere + ".lvh.test", nothing + ".lvh.test");
  }

  @Test
  void removingAnUnknownAliasIsNotFound() {
    String alias = newServiceName("nothing");

    ResponseEntity<JsonNode> response = delete(aliasPath(alias));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody().get("detail").asString()).contains(alias);
  }
}
