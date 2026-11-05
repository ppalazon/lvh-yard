package dev.palazon.lvhyard.cli;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Client for the dashboard's REST API ({@code openapi.yaml}). Every failure is a {@link
 * YardException} with a message ready to print. A {@code publish} argument of {@code false} leaves
 * a new Service Unpublished, and {@code null} lets the dashboard publish it.
 */
final class Dashboard {

  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);

  /** How long an answer may take once connected; the dashboard reads only its local Consul. */
  private static final Duration ANSWER_TIMEOUT = Duration.ofSeconds(10);

  private static final ObjectMapper JSON =
      JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

  private final URI url;

  private final HttpClient http = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();

  /** {@code url} with a trailing /, so the API's paths resolve below any path it has. */
  Dashboard(URI url) {
    this.url = url.toString().endsWith("/") ? url : URI.create(url + "/");
  }

  /** The API leaves {@code routing} out when this Workstation does not route the Service. */
  record Service(
      String workstation,
      String name,
      String state,
      Publication publication,
      String hostname,
      List<Instance> instances,
      List<ContainerBinding> containerBindings,
      List<Warning> warnings,
      List<String> profiles,
      Routing routing) {

    boolean routes(Instance instance) {
      return routing != null && routing.instances().contains(instance.id());
    }
  }

  /** {@code kind} is left out when no Instance passes, and the Route then uses every one. */
  /** The API leaves {@code off} out when it is false. */
  record Routing(String kind, Boolean off, List<String> instances) {

    boolean isOff() {
      return Boolean.TRUE.equals(off);
    }
  }

  record ContainerBinding(
      String id,
      String container,
      String composeProject,
      String composeService,
      int port,
      List<String> instances) {

    /** What it follows: {@code container web-1}, or {@code Compose service web of project shop}. */
    String follows() {
      return container != null
          ? "container " + container
          : "Compose service " + composeService + " of project " + composeProject;
    }
  }

  record Warning(String kind, String message) {}

  /**
   * The API leaves {@code composeProject}, {@code composeService} and {@code health} out when the
   * container has none.
   */
  record UnfollowedContainer(
      String name,
      String composeProject,
      String composeService,
      String service,
      List<ContainerPort> ports,
      String health) {}

  /** {@code hostPort} is null when the container port is not published. */
  record ContainerPort(int containerPort, Integer hostPort) {}

  /**
   * {@code type} is null unless it has exactly one. {@code services} are the Services whose
   * Publications name it.
   */
  record RouteProfile(String name, String type, List<String> services) {}

  /** {@code definition} is the middleware in Traefik's format, such as {@code {"headers": {}}}. */
  record RouteProfileDetail(
      String name, String type, List<String> services, Map<String, Object> definition) {}

  /** This Workstation, then the others by name. */
  record Yard(HomeWorkstation home, List<YardWorkstation> remotes) {}

  record HomeWorkstation(String name, List<Service> services) {}

  /** An unreachable one has no Services for now. */
  record YardWorkstation(String name, boolean unreachable, List<Service> services) {}

  /**
   * {@code state} is active, unavailable, fallback, hidden, not-shared, unreachable or missing.
   * Only an active one has a Route.
   */
  record Alias(
      String name,
      String workstation,
      String service,
      String hostname,
      String state,
      String reason) {

    boolean active() {
      return "active".equals(state);
    }
  }

  record Publication(String scope) {}

  /** {@code health} is passing, warning or critical. */
  record Instance(String id, String host, int port, String health, boolean outOfRange) {

    String address() {
      return Dashboard.address(host, port);
    }
  }

  /** A {@code null} part gets the dashboard's default. */
  record Check(String type, String path, String interval, String timeout) {

    Map<String, String> given() {
      Map<String, String> parts = new LinkedHashMap<>();
      parts.put("type", type);
      parts.put("path", path);
      parts.put("interval", interval);
      parts.put("timeout", timeout);
      parts.values().removeIf(Objects::isNull);
      return parts;
    }
  }

  record Reachability(
      String workstation,
      String service,
      List<InstanceReachability> instances,
      List<RouteReachability> routes,
      String noRoute,
      Routing routing,
      Alias alias,
      boolean passed) {}

  /** The API leaves {@code outOfRange} and {@code routed} out when they are false. */
  record InstanceReachability(
      String id,
      String address,
      boolean reached,
      Boolean outOfRange,
      Boolean routed,
      String error) {

    boolean skipped() {
      return Boolean.TRUE.equals(outOfRange);
    }

    boolean isRouted() {
      return Boolean.TRUE.equals(routed);
    }
  }

  record RouteReachability(String hostname, boolean reached, Integer status, String error) {}

  /** {@code overall} is the state of the worst part. */
  record Status(String overall, List<StatusPart> parts) {}

  record StatusPart(String name, String state, String reason, List<StatusDetail> details) {}

  /** One thing found about a part; {@code state} is null for a plain fact such as a version. */
  record StatusDetail(String name, String value, String state) {}

  /** {@code host:port}, with an IPv6 host in brackets so its colons are not read as the port's. */
  static String address(String host, int port) {
    return (host.contains(":") ? "[" + host + "]" : host) + ":" + port;
  }

  Status status() {
    return read(send(get("api/status")), new TypeReference<>() {});
  }

  Yard yard() {
    return read(send(get("api/yard")), new TypeReference<>() {});
  }

  List<Service> services(String workstation) {
    return read(
        send(
            get(
                "api/services?workstation="
                    + URLEncoder.encode(workstation, StandardCharsets.UTF_8))),
        new TypeReference<>() {});
  }

  Service ownService(String service) {
    return read(
        send(get("api/workstation/services/" + segment(service))), new TypeReference<>() {});
  }

  Reachability testOwn(String service) {
    return read(
        send(get("api/workstation/services/" + segment(service) + "/reachability")),
        new TypeReference<>() {});
  }

  Reachability test(String workstation, String service) {
    return read(
        send(
            get("api/services/" + segment(workstation) + "/" + segment(service) + "/reachability")),
        new TypeReference<>() {});
  }

  /**
   * With no host, the dashboard uses this Workstation's LAN address. A part of the check left out
   * gets the dashboard's default.
   */
  Instance register(
      String service,
      String host,
      int port,
      Check check,
      List<String> tags,
      Map<String, String> meta,
      Boolean publish) {
    Map<String, Object> instance = new LinkedHashMap<>();
    if (host != null) {
      instance.put("host", host);
    }
    instance.put("port", port);
    if (!check.given().isEmpty()) {
      instance.put("check", check.given());
    }
    if (!tags.isEmpty()) {
      instance.put("tags", tags);
    }
    if (!meta.isEmpty()) {
      instance.put("meta", meta);
    }
    if (publish != null) {
      instance.put("publish", publish);
    }
    HttpRequest.Builder request =
        HttpRequest.newBuilder(instancesUrl(service))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(instance)));
    return read(send(request), new TypeReference<>() {});
  }

  ContainerBinding bindCompose(
      String service, String project, String composeService, int port, Boolean publish) {
    Map<String, Object> binding = new LinkedHashMap<>();
    binding.put("composeProject", project);
    binding.put("composeService", composeService);
    binding.put("port", port);
    if (publish != null) {
      binding.put("publish", publish);
    }
    HttpRequest.Builder request =
        HttpRequest.newBuilder(containerBindingsUrl(service))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(binding)));
    return read(send(request), new TypeReference<>() {});
  }

  /** Compose projects first, then the other containers. */
  List<UnfollowedContainer> containers() {
    return read(send(get("api/workstation/containers")), new TypeReference<>() {});
  }

  /** By name. */
  /** The Backup of this Workstation's setup, as the YAML text that the dashboard writes. */
  String backup() {
    return send(get("api/workstation/backup"), "application/yaml, application/problem+json").body();
  }

  /**
   * Sends the Backup's YAML text. With {@code dryRun}, the dashboard answers the plan and changes
   * nothing.
   */
  ImportResult importBackup(String backup, String mode, boolean dryRun) {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(
                url.resolve(
                    "api/workstation/backup?mode="
                        + segment(mode)
                        + (dryRun ? "&dry-run=true" : "")))
            .header("Content-Type", "application/yaml")
            .POST(HttpRequest.BodyPublishers.ofString(backup, StandardCharsets.UTF_8));
    return read(send(request), new TypeReference<>() {});
  }

  /** {@code exportedFrom} is left out when the Backup does not say. */
  record ImportResult(
      ExportedFrom exportedFrom, String mode, boolean dryRun, List<PlanEntry> entries) {}

  record ExportedFrom(String workstation, String at) {}

  /** {@code service} is left out but for a Static Instance or a Container Binding. */
  record PlanEntry(String kind, String name, String service, String action, String reason) {}

  List<RouteProfile> routeProfiles() {
    return read(send(get("api/workstation/route-profiles")), new TypeReference<>() {});
  }

  RouteProfileDetail routeProfile(String name) {
    return read(send(get(routeProfilePath(name))), new TypeReference<>() {});
  }

  /**
   * Adds or replaces each profile of {@code profiles}, a map of names to middlewares. All or none
   * are saved, and the others stay. Answers the whole list.
   */
  List<RouteProfile> putRouteProfiles(Map<String, Object> profiles) {
    return read(
        send(putJson("api/workstation/route-profiles", profiles)), new TypeReference<>() {});
  }

  /** Adds or replaces the profile {@code name} with {@code middleware}, Traefik's middleware. */
  RouteProfileDetail putRouteProfile(String name, Object middleware) {
    return read(send(putJson(routeProfilePath(name), middleware)), new TypeReference<>() {});
  }

  void removeRouteProfile(String name) {
    send(HttpRequest.newBuilder(url.resolve(routeProfilePath(name))).DELETE());
  }

  private static String routeProfilePath(String name) {
    return "api/workstation/route-profiles/" + segment(name);
  }

  /** A {@code null} argument gets the dashboard's default. */
  Service registerContainer(String container, Integer port, String service, Boolean publish) {
    Map<String, Object> body = new LinkedHashMap<>();
    if (port != null) {
      body.put("port", port);
    }
    if (service != null) {
      body.put("service", service);
    }
    if (publish != null) {
      body.put("publish", publish);
    }
    HttpRequest.Builder request =
        HttpRequest.newBuilder(
                url.resolve("api/workstation/containers/" + segment(container) + "/register"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
    return read(send(request), new TypeReference<>() {});
  }

  void unbind(String service, String id) {
    send(
        HttpRequest.newBuilder(URI.create(containerBindingsUrl(service) + "/" + segment(id)))
            .DELETE());
  }

  void remove(String service, String id) {
    send(HttpRequest.newBuilder(URI.create(instancesUrl(service) + "/" + segment(id))).DELETE());
  }

  /**
   * With no Scope or no priority, a new Publication gets Local Scope or the default order, and an
   * existing one keeps its own.
   */
  /** Sends only what is not {@code null}, so the Publication keeps the rest. */
  Service publish(String service, String scope, List<String> priority, List<String> profiles) {
    HttpRequest.Builder request = HttpRequest.newBuilder(publicationUrl(service));
    Map<String, Object> body = new LinkedHashMap<>();
    if (scope != null) {
      body.put("scope", scope);
    }
    if (priority != null) {
      body.put("priority", priority);
    }
    if (profiles != null) {
      body.put("profiles", profiles);
    }
    if (body.isEmpty()) {
      request.PUT(HttpRequest.BodyPublishers.noBody());
    } else {
      request
          .header("Content-Type", "application/json")
          .PUT(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
    }
    return read(send(request), new TypeReference<>() {});
  }

  void unpublish(String service) {
    send(HttpRequest.newBuilder(publicationUrl(service)).DELETE());
  }

  List<Alias> aliases() {
    return read(send(get("api/workstation/aliases")), new TypeReference<>() {});
  }

  Alias addAlias(String name, String workstation, String service) {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(aliasUrl(name))
            .header("Content-Type", "application/json")
            .PUT(
                HttpRequest.BodyPublishers.ofString(
                    JSON.writeValueAsString(
                        Map.of("workstation", workstation, "service", service))));
    return read(send(request), new TypeReference<>() {});
  }

  void removeAlias(String name) {
    send(HttpRequest.newBuilder(aliasUrl(name)).DELETE());
  }

  private URI aliasUrl(String name) {
    return url.resolve("api/workstation/aliases/" + segment(name));
  }

  private URI publicationUrl(String service) {
    return url.resolve("api/workstation/services/" + segment(service) + "/publication");
  }

  private URI containerBindingsUrl(String service) {
    return url.resolve("api/workstation/services/" + segment(service) + "/container-bindings");
  }

  private URI instancesUrl(String service) {
    return url.resolve("api/workstation/services/" + segment(service) + "/instances");
  }

  private HttpRequest.Builder get(String path) {
    return HttpRequest.newBuilder(url.resolve(path)).GET();
  }

  private HttpRequest.Builder putJson(String path, Object body) {
    return HttpRequest.newBuilder(url.resolve(path))
        .header("Content-Type", "application/json")
        .PUT(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
  }

  /** {@code value} as one segment of a path, so a / or a space in a name cannot change the path. */
  private static String segment(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
  }

  /** A server that is not the dashboard may answer without JSON. */
  private <T> T read(HttpResponse<String> response, TypeReference<T> type) {
    try {
      return JSON.readValue(response.body(), type);
    } catch (JacksonException e) {
      throw new YardException(
          url + " is not the LVH Yard dashboard: its answer is not the API's JSON.");
    }
  }

  private HttpResponse<String> send(HttpRequest.Builder request) {
    return send(request, "application/json");
  }

  private HttpResponse<String> send(HttpRequest.Builder request, String accept) {
    HttpResponse<String> response;
    try {
      response =
          http.send(
              request.header("Accept", accept).timeout(ANSWER_TIMEOUT).build(),
              HttpResponse.BodyHandlers.ofString());
    } catch (ConnectException | HttpConnectTimeoutException e) {
      throw new YardException(
          "The dashboard does not answer at "
              + url
              + ". Start it with docker compose up -d in the folder that lvh-yard init wrote.");
    } catch (HttpTimeoutException e) {
      throw new YardException(
          "The dashboard at "
              + url
              + " did not answer within "
              + ANSWER_TIMEOUT.toSeconds()
              + " s. Make sure that Consul runs: docker compose up -d in the folder that lvh-yard"
              + " init wrote.");
    } catch (IOException e) {
      throw new YardException("The dashboard at " + url + " cannot be reached: " + e.getMessage());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new YardException("Interrupted while waiting for the dashboard at " + url + ".");
    }
    if (response.statusCode() >= 400) {
      throw new YardException(reason(response));
    }
    return response;
  }

  /**
   * Each reason of the Problem Details' {@code problems} on a line of its own, after its place in
   * the file when it has one, else its detail, else its title, else the status.
   */
  private static String reason(HttpResponse<String> response) {
    try {
      JsonNode problem = JSON.readTree(response.body());
      List<String> reasons =
          problem.path("problems").valueStream().map(p -> p.path("reason").asString("")).toList();
      if (!reasons.isEmpty() && !reasons.contains("")) {
        return String.join(
            "\n",
            problem
                .path("problems")
                .valueStream()
                .map(p -> {
                  String place = p.path("place").asString("");
                  String reason = p.path("reason").asString();
                  return place.isEmpty() ? reason : place + ": " + reason;
                })
                .toList());
      }
      for (String member : List.of("detail", "title")) {
        if (problem.path(member).isString() && !problem.get(member).asString().isBlank()) {
          return problem.get(member).asString();
        }
      }
    } catch (JacksonException e) {
      // Not a Problem Details document: the status is all there is.
    }
    return "The dashboard refused with HTTP status " + response.statusCode() + ".";
  }
}
