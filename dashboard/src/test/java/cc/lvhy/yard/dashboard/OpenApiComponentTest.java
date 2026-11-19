package cc.lvhy.yard.dashboard;

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
class OpenApiComponentTest extends DashboardComponentTest {

  private static final Path COMMITTED_DOCUMENT = Path.of("openapi.yaml");

  /** Set by {@code mise run openapi}: the committed copy is rewritten instead of compared. */
  private static final boolean UPDATE = Boolean.getBoolean("openapi.update");

  @Test
  void serviceStateAndFilterIncludeDegradedInStateOrder() {
    JsonNode document = get("/v3/api-docs").getBody();
    assertThat(
            document
                .get("components")
                .get("schemas")
                .get("Service")
                .get("properties")
                .get("state")
                .get("enum")
                .valueStream()
                .map(JsonNode::asString))
        .containsExactly("unpublished", "unavailable", "unhealthy", "degraded", "healthy");
    JsonNode state =
        document
            .get("paths")
            .get("/api/services")
            .get("get")
            .get("parameters")
            .valueStream()
            .filter(parameter -> parameter.get("name").asString().equals("state"))
            .findFirst()
            .orElseThrow();
    assertThat(state.get("schema").get("enum").valueStream().map(JsonNode::asString))
        .containsExactly("unpublished", "unavailable", "unhealthy", "degraded", "healthy");
  }

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
            "/api/workstation/backup",
            "/api/workstation/containers",
            "/api/workstation/route-profiles",
            "/api/workstation/route-profiles/{name}",
            "/api/workstation/services",
            "/api/workstation/services/{service}",
            "/api/workstation/services/{service}/aliases",
            "/api/workstation/services/{service}/aliases/{workstation}/{remoteService}",
            "/api/workstation/services/{service}/container-bindings",
            "/api/workstation/services/{service}/container-bindings/{id}",
            "/api/workstation/services/{service}/instances",
            "/api/workstation/services/{service}/instance-priority",
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
    String services = "/api/workstation/services";
    String service = services + "/{service}";
    assertThat(answersWithProblem(paths, services, "post", "400")).isTrue();
    assertThat(answersWithProblem(paths, service, "get", "404")).isTrue();
    assertThat(answersWithProblem(paths, service, "delete", "404")).isTrue();
    assertThat(answersWithProblem(paths, service + "/instances", "post", "400")).isTrue();
    assertThat(answersWithProblem(paths, service + "/instances", "post", "404")).isTrue();
    assertThat(answersWithProblem(paths, service + "/instances/{id}", "delete", "404")).isTrue();
    assertThat(answersWithProblem(paths, service + "/container-bindings", "post", "404")).isTrue();
    assertThat(answersWithProblem(paths, service + "/publication", "put", "404")).isTrue();
    assertThat(answersWithProblem(paths, service + "/publication", "delete", "404")).isTrue();
    String aliases = service + "/aliases";
    for (String status : new String[] {"400", "404"}) {
      assertThat(answersWithProblem(paths, aliases, "post", status)).as(status).isTrue();
    }
    assertThat(
            answersWithProblem(paths, aliases + "/{workstation}/{remoteService}", "delete", "404"))
        .isTrue();
    assertThat(answersWithProblem(paths, "/api/workstation/containers", "get", "503")).isTrue();
    String collection = "/api/workstation/route-profiles";
    String profile = collection + "/{name}";
    assertThat(answersWithProblem(paths, collection, "put", "400")).isTrue();
    assertThat(answersWithProblem(paths, profile, "get", "404")).isTrue();
    assertThat(answersWithProblem(paths, profile, "put", "400")).isTrue();
    assertThat(answersWithProblem(paths, profile, "delete", "404")).isTrue();
    assertThat(answersWithProblem(paths, profile, "delete", "409")).isTrue();
    assertThat(answersWithProblem(paths, "/api/workstation/backup", "post", "400")).isTrue();
    assertThat(answersWithProblem(paths, "/api/workstation/backup", "post", "500")).isTrue();
  }

  @Test
  void containerListGivesEveryContainerWithItsStateAndFollowingService() {
    JsonNode schema =
        get("/v3/api-docs").getBody().get("components").get("schemas").get("ListedContainer");

    assertThat(schema.get("required").valueStream().map(JsonNode::asString))
        .contains("name", "running", "ports");
    assertThat(schema.get("properties").has("followedBy")).isTrue();
  }

  @Test
  void servicesAreCreatedAndDeleted() {
    JsonNode docs = get("/v3/api-docs").getBody();
    JsonNode paths = docs.get("paths");
    String services = "/api/workstation/services";
    String service = services + "/{service}";

    assertThat(paths.path(services).path("post").path("operationId").asString())
        .isEqualTo("createService");
    assertThat(paths.path(service).path("delete").path("operationId").asString())
        .isEqualTo("deleteService");
    JsonNode schemas = docs.get("components").get("schemas");
    assertThat(schemas.get("Service").get("required").valueStream().map(JsonNode::asString))
        .contains("created", "empty");
  }

  @Test
  void servicesHaveTheirOrderedSourcesTheToggleAndRouting() {
    JsonNode schemas = get("/v3/api-docs").getBody().get("components").get("schemas");

    JsonNode service = schemas.get("Service").get("properties");
    assertThat(service.get("instancePriority").get("type").asString()).isEqualTo("boolean");
    assertThat(service.get("sources").get("items").get("$ref").asString())
        .isEqualTo("#/components/schemas/InstanceSource");
    assertThat(
            schemas.get("InstanceSource").get("properties").get("kind").get("enum").valueStream())
        .map(JsonNode::asString)
        .containsExactly("static", "bound", "alias", "foreign");
    assertThat(service.get("routing").get("$ref").asString())
        .isEqualTo("#/components/schemas/Routing");
    JsonNode routing = schemas.get("Routing").get("properties");
    assertThat(routing.has("source")).isTrue();
    assertThat(routing.get("waiting").get("type").asString()).isEqualTo("array");
    assertThat(routing.get("instances").get("type").asString()).isEqualTo("array");
  }

  @Test
  void publishingTakesRouteProfilesAndServicesShowThem() {
    JsonNode schemas = get("/v3/api-docs").getBody().get("components").get("schemas");

    JsonNode profiles = schemas.get("PublicationRequest").get("properties").get("profiles");
    assertThat(profiles.get("type").asString()).isEqualTo("array");
    assertThat(profiles.get("items").get("type").asString()).isEqualTo("string");
    assertThat(schemas.get("Service").get("properties").get("profiles").get("type").asString())
        .isEqualTo("array");
    assertThat(schemas.get("Publication").get("properties").has("profiles")).isFalse();
    assertThat(
            schemas.get("ServiceWarning").get("properties").get("kind").get("enum").valueStream())
        .map(JsonNode::asString)
        .contains("route-profile-auth");
  }

  @Test
  void routeProfilesAreListedReadSavedAndRemoved() {
    JsonNode docs = get("/v3/api-docs").getBody();
    JsonNode paths = docs.get("paths");
    String collection = "/api/workstation/route-profiles";
    String one = collection + "/{name}";

    assertThat(paths.get(collection).propertyNames()).containsExactlyInAnyOrder("get", "put");
    assertThat(paths.get(one).propertyNames()).containsExactlyInAnyOrder("get", "put", "delete");
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
  }

  @Test
  void backupIsImportedFromYamlAndAnswersThePlan() {
    JsonNode docs = get("/v3/api-docs").getBody();
    JsonNode paths = docs.get("paths");
    JsonNode post = paths.get("/api/workstation/backup").get("post");

    assertThat(post.get("requestBody").get("content").propertyNames())
        .containsExactly("application/yaml");
    assertThat(post.get("parameters").valueStream().map(parameter -> parameter.get("name")))
        .extracting(JsonNode::asString)
        .containsExactlyInAnyOrder("mode", "dry-run");
    JsonNode schemas = docs.get("components").get("schemas");
    assertThat(schemas.get("ImportResult").get("properties").propertyNames())
        .containsExactlyInAnyOrder("exportedFrom", "mode", "dryRun", "entries", "safetyBackup");
    assertThat(schemas.get("PlanEntry").get("properties").propertyNames())
        .containsExactlyInAnyOrder("kind", "name", "service", "action", "reason");
  }

  @Test
  void backupIsExportedAsYaml() {
    JsonNode get =
        get("/v3/api-docs").getBody().get("paths").get("/api/workstation/backup").get("get");

    assertThat(get.get("responses").get("200").get("content").propertyNames())
        .containsExactly("application/yaml");
  }

  @Test
  void instancePriorityIsReadAndSetApartFromThePublication() {
    JsonNode docs = get("/v3/api-docs").getBody();

    JsonNode path = docs.get("paths").get("/api/workstation/services/{service}/instance-priority");
    assertThat(path.has("get")).isTrue();
    assertThat(path.get("put").get("responses").propertyNames()).contains("200", "400", "404");
    assertThat(
            docs.get("components")
                .get("schemas")
                .get("InstancePriorityRequest")
                .get("properties")
                .propertyNames())
        .containsExactlyInAnyOrder("order", "on");
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
