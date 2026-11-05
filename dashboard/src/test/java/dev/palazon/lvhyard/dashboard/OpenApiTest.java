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
            "/api/workstation/backup",
            "/api/workstation/containers",
            "/api/workstation/containers/{container}/register",
            "/api/workstation/route-profiles",
            "/api/workstation/route-profiles/{name}",
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
    assertThat(answersWithProblem(paths, "/api/workstation/containers", "get", "503")).isTrue();
    String registerPath = "/api/workstation/containers/{container}/register";
    assertThat(answersWithProblem(paths, registerPath, "post", "400")).isTrue();
    assertThat(answersWithProblem(paths, registerPath, "post", "503")).isTrue();
  }

  @Test
  void changesToAServiceAreNeverRefusedWith409() {
    JsonNode paths = get("/v3/api-docs").getBody().get("paths");
    String instance = "/api/workstation/services/{service}/instances/{id}";
    String binding = "/api/workstation/services/{service}/container-bindings/{id}";
    String publication = "/api/workstation/services/{service}/publication";

    assertThat(paths.path(instance).path("delete").path("responses").has("409")).isFalse();
    assertThat(paths.path(instance).path("put").path("responses").has("409")).isFalse();
    assertThat(paths.path(binding).path("delete").path("responses").has("409")).isFalse();
    assertThat(paths.path(publication).path("put").path("responses").has("409")).isFalse();
    assertThat(paths.path(publication).path("delete").path("responses").has("409")).isFalse();
  }

  @Test
  void servicesAndInstancesHaveNoCoreField() {
    JsonNode schemas = get("/v3/api-docs").getBody().get("components").get("schemas");

    for (String schema : new String[] {"Service", "Instance"}) {
      assertThat(schemas.get(schema).get("properties").has("core")).as(schema).isFalse();
    }
  }

  @Test
  void documentationSaysNothingAboutCoreServices() {
    assertThat(getPage("/v3/api-docs.yaml").getBody()).doesNotContainIgnoringCase("core service");
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
  void reachabilitySaysWhereTheRouteSendsRequests() {
    JsonNode schemas = get("/v3/api-docs").getBody().get("components").get("schemas");

    JsonNode reachability = schemas.get("Reachability").get("properties");
    assertThat(reachability.get("routing").get("$ref").asString())
        .isEqualTo("#/components/schemas/Routing");
    assertThat(reachability.get("alias").get("$ref").asString())
        .isEqualTo("#/components/schemas/Alias");
    assertThat(
            schemas
                .get("InstanceReachability")
                .get("properties")
                .get("routed")
                .get("type")
                .asString())
        .isEqualTo("boolean");
  }

  @Test
  void publishingTakesRouteProfilesAndServicesShowThem() {
    JsonNode docs = get("/v3/api-docs").getBody();
    JsonNode schemas = docs.get("components").get("schemas");

    JsonNode profiles = schemas.get("PublicationRequest").get("properties").get("profiles");
    assertThat(profiles.get("type").asString()).isEqualTo("array");
    assertThat(profiles.get("items").get("type").asString()).isEqualTo("string");
    assertThat(schemas.get("Service").get("properties").get("profiles").get("type").asString())
        .isEqualTo("array");
    assertThat(schemas.get("Service").get("properties").has("profileDefinitions")).isFalse();
    assertThat(schemas.get("Publication").get("properties").has("profiles")).isFalse();
    assertThat(
            schemas.get("ServiceWarning").get("properties").get("kind").get("enum").valueStream())
        .map(JsonNode::asString)
        .contains("route-profile-auth");
    JsonNode put =
        docs.get("paths").get("/api/workstation/services/{service}/publication").get("put");
    assertThat(put.get("description").asString()).contains("Route Profiles");
    assertThat(put.get("responses").get("400").get("description").asString())
        .contains("Route Profile");
  }

  @Test
  void routeProfilesAreListedReadSavedAndRemoved() {
    JsonNode docs = get("/v3/api-docs").getBody();
    JsonNode paths = docs.get("paths");
    String collection = "/api/workstation/route-profiles";
    String one = collection + "/{name}";

    assertThat(paths.get(collection).propertyNames()).containsExactlyInAnyOrder("get", "put");
    assertThat(paths.get(one).propertyNames()).containsExactlyInAnyOrder("get", "put", "delete");
    assertThat(answersWithProblem(paths, collection, "put", "400")).isTrue();
    assertThat(answersWithProblem(paths, one, "get", "404")).isTrue();
    assertThat(answersWithProblem(paths, one, "put", "400")).isTrue();
    assertThat(answersWithProblem(paths, one, "delete", "404")).isTrue();
    assertThat(answersWithProblem(paths, one, "delete", "409")).isTrue();
    JsonNode list =
        paths
            .get(collection)
            .get("get")
            .get("responses")
            .get("200")
            .get("content")
            .get("application/json");
    assertThat(list.get("schema").get("type").asString()).isEqualTo("array");
    JsonNode schemas = docs.get("components").get("schemas");
    assertThat(schemas.get("RouteProfile").get("properties").propertyNames())
        .containsExactlyInAnyOrder("name", "type", "services");
    assertThat(schemas.get("RouteProfileDetail").get("properties").propertyNames())
        .containsExactlyInAnyOrder("name", "type", "services", "definition");
    assertThat(schemas.has("RouteProfilesFile")).isFalse();
  }

  @Test
  void backupIsExportedAsYaml() {
    JsonNode get =
        get("/v3/api-docs").getBody().get("paths").get("/api/workstation/backup").get("get");

    assertThat(get.get("responses").get("200").get("content").propertyNames())
        .containsExactly("application/yaml");
    assertThat(get.get("description").asString()).contains("basic-auth hashes");
  }

  @Test
  void publishingTakesAnInstancePriority() {
    JsonNode request =
        get("/v3/api-docs")
            .getBody()
            .get("components")
            .get("schemas")
            .get("PublicationRequest")
            .get("properties");

    assertThat(request.get("priority").get("type").asString()).isEqualTo("array");
    assertThat(request.get("priority").get("items").get("enum").valueStream())
        .map(JsonNode::asString)
        .containsExactly("static", "bound", "alias");
    assertThat(
            get("/v3/api-docs")
                .getBody()
                .get("paths")
                .get("/api/workstation/services/{service}/publication")
                .get("put")
                .get("description")
                .asString())
        .contains("An empty priority turns the Instance Priority off");
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
