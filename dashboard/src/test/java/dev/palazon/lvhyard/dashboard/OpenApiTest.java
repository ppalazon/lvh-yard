package dev.palazon.lvhyard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/**
 * The OpenAPI document of the REST API, the CLI's contract. A copy is committed at {@code
 * dashboard/openapi.yaml}; {@code mise run openapi} rewrites it.
 */
class OpenApiTest extends DashboardTest {

  private static final Path COMMITTED_DOCUMENT = Path.of("openapi.yaml");

  /** Set by {@code mise run openapi}: the committed copy is rewritten instead of compared. */
  private static final boolean UPDATE = Boolean.getBoolean("openapi.update");

  @Test
  void documentListsEveryApiPath() {
    ResponseEntity<JsonNode> response = get("/v3/api-docs");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().get("paths").propertyNames())
        .containsExactlyInAnyOrder(
            "/api/services",
            "/api/services/{workstation}/{service}",
            "/api/services/{workstation}/{service}/reachability",
            "/api/status",
            "/api/yard",
            "/api/workstation/aliases",
            "/api/workstation/aliases/{alias}",
            "/api/workstation/services",
            "/api/workstation/services/{service}",
            "/api/workstation/services/{service}/container-bindings",
            "/api/workstation/services/{service}/container-bindings/{id}",
            "/api/workstation/services/{service}/instances",
            "/api/workstation/services/{service}/instances/{id}",
            "/api/workstation/services/{service}/publication",
            "/api/workstation/services/{service}/reachability");
  }

  @Test
  void errorsAreDescribedAsProblemDetails() {
    JsonNode paths = get("/v3/api-docs").getBody().get("paths");

    assertThat(answersWithProblem(paths, "/api/services", "get", "503")).isTrue();
    assertThat(answersWithProblem(paths, "/api/services/{workstation}/{service}", "get", "404"))
        .isTrue();
    assertThat(answersWithProblem(paths, "/api/services/{workstation}/{service}", "get", "503"))
        .isTrue();
    assertThat(answersWithProblem(paths, "/api/workstation/services/{service}", "get", "404"))
        .isTrue();
    assertThat(
            answersWithProblem(
                paths, "/api/workstation/services/{service}/instances", "post", "400"))
        .isTrue();
    assertThat(
            answersWithProblem(
                paths, "/api/workstation/services/{service}/instances/{id}", "delete", "404"))
        .isTrue();
    assertThat(
            answersWithProblem(
                paths, "/api/workstation/services/{service}/publication", "put", "404"))
        .isTrue();
    assertThat(
            answersWithProblem(
                paths, "/api/workstation/services/{service}/publication", "delete", "404"))
        .isTrue();
    assertThat(answersWithProblem(paths, "/api/workstation/aliases/{alias}", "put", "400"))
        .isTrue();
    assertThat(answersWithProblem(paths, "/api/workstation/aliases/{alias}", "delete", "404"))
        .isTrue();
  }

  @Test
  void changesToACoreServiceAreRefusedWithProblemDetails() {
    JsonNode paths = get("/v3/api-docs").getBody().get("paths");
    String instance = "/api/workstation/services/{service}/instances/{id}";
    String binding = "/api/workstation/services/{service}/container-bindings/{id}";
    String publication = "/api/workstation/services/{service}/publication";

    assertThat(answersWithProblem(paths, instance, "delete", "409")).isTrue();
    assertThat(answersWithProblem(paths, instance, "put", "409")).isTrue();
    assertThat(answersWithProblem(paths, binding, "delete", "409")).isTrue();
    assertThat(answersWithProblem(paths, publication, "put", "409")).isTrue();
    assertThat(answersWithProblem(paths, publication, "delete", "409")).isTrue();
  }

  @Test
  void servicesAndInstancesSayWhetherTheyAreCore() {
    JsonNode schemas = get("/v3/api-docs").getBody().get("components").get("schemas");

    for (String schema : new String[] {"Service", "Instance"}) {
      JsonNode core = schemas.get(schema).get("properties").get("core");
      assertThat(core).as("%s has a core flag", schema).isNotNull();
      assertThat(core.get("type").asString()).isEqualTo("boolean");
    }
  }

  @Test
  void servicesHaveTheirInstancePriorityAndRouting() {
    JsonNode schemas = get("/v3/api-docs").getBody().get("components").get("schemas");

    JsonNode service = schemas.get("Service").get("properties");
    assertThat(service.get("priority").get("type").asString()).isEqualTo("array");
    assertThat(service.get("priority").get("items").get("enum").valueStream())
        .map(JsonNode::asString)
        .containsExactly("static", "bound", "alias");
    assertThat(service.get("routing").get("$ref").asString())
        .isEqualTo("#/components/schemas/Routing");
    JsonNode routing = schemas.get("Routing").get("properties");
    assertThat(routing.has("kind")).isTrue();
    assertThat(routing.get("instances").get("type").asString()).isEqualTo("array");
    assertThat(schemas.get("Publication").get("properties").has("priority")).isFalse();
  }

  @Test
  void committedCopyIsCurrent() throws IOException {
    ResponseEntity<String> response = getPage("/v3/api-docs.yaml");
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

    if (UPDATE) {
      Files.writeString(COMMITTED_DOCUMENT, response.getBody());
    }
    assertThat(COMMITTED_DOCUMENT).exists();
    assertThat(Files.readString(COMMITTED_DOCUMENT))
        .as("%s is stale; run mise run openapi", COMMITTED_DOCUMENT)
        .isEqualTo(response.getBody());
  }

  /**
   * Whether {@code method} on {@code path} answers {@code status} with a Problem Details document.
   */
  private static boolean answersWithProblem(
      JsonNode paths, String path, String method, String status) {
    return paths
        .path(path)
        .path(method)
        .path("responses")
        .path(status)
        .path("content")
        .has("application/problem+json");
  }
}
