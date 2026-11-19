package cc.lvhy.yard.dashboard.web;

import cc.lvhy.yard.dashboard.Workstation;
import cc.lvhy.yard.dashboard.services.ContainerList;
import cc.lvhy.yard.dashboard.services.CreateContainerService;
import cc.lvhy.yard.dashboard.services.InvalidRegistrationException;
import cc.lvhy.yard.dashboard.services.ListedContainer;
import cc.lvhy.yard.dashboard.services.Scope;
import cc.lvhy.yard.dashboard.services.UnreadableDockerException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

/** Browses this Workstation's containers and creates a Service from one target. */
@Controller
class ContainersPagesController {
  private final Workstation workstation;
  private final ContainerList containers;
  private final CreateContainerService creation;

  ContainersPagesController(
      Workstation workstation, ContainerList containers, CreateContainerService creation) {
    this.workstation = workstation;
    this.containers = containers;
    this.creation = creation;
  }

  @GetMapping(path = ContainersPage.PATH, produces = MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  String containers(
      @RequestParam(required = false) String state,
      @RequestParam(required = false) String followed) {
    var filters =
        new ContainersPage.Filters(
            blank(state)
                ? ContainersPage.Filters.DEFAULT.state()
                : ContainersPage.StateFilter.named(state)
                    .orElseThrow(() ->
                        new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "The state " + state + " is unknown. Use running, stopped or all.")),
            blank(followed)
                ? ContainersPage.Filters.DEFAULT.followed()
                : ContainersPage.FollowedFilter.named(followed)
                    .orElseThrow(() ->
                        new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "The value "
                                + followed
                                + " is unknown. Use no, yes or all for followed.")));
    List<ListedContainer> listed = List.of();
    String problem = null;
    try {
      listed = containers.containers();
    } catch (UnreadableDockerException e) {
      problem = e.getMessage();
    }
    return ContainersPage.render(workstation, listed, filters, problem);
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }

  @GetMapping(path = CreateContainerServicePage.PATH, produces = MediaType.TEXT_HTML_VALUE)
  ResponseEntity<String> form(@RequestParam MultiValueMap<String, String> params) {
    if (!oneTarget(params.get("target"))) {
      return invalidTarget();
    }
    return ResponseEntity.ok()
        .contentType(Layout.HTML)
        .body(form(params.getFirst("target"), params.getFirst("port"), null, null));
  }

  @PostMapping(path = CreateContainerServicePage.ACTION, produces = MediaType.TEXT_HTML_VALUE)
  ResponseEntity<String> create(@RequestParam MultiValueMap<String, String> params) {
    if (!oneTarget(params.get("target"))) {
      return invalidTarget();
    }
    String target = params.getFirst("target");
    String port = params.getFirst("containerPort");
    String name = params.getFirst("name");
    String scope = params.getFirst("scope");
    var fields =
        new CreateContainerServicePage.Fields(
            name == null ? "" : name,
            new CreateContainerServicePage.Target(target, port == null ? "" : port, List.of()),
            "true".equals(params.getFirst("publish")),
            scope == null ? "" : scope);
    try {
      Scope chosen =
          Scope.named(fields.scope())
              .orElseThrow(
                  () -> new InvalidRegistrationException("scope", "Choose Local or Yard Scope."));
      var binding = new BindContainerPage.Bind(target, fields.target().port()).request();
      var result = creation.create(fields.name(), binding, fields.publish(), chosen);
      if (result.failure() != null) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .contentType(Layout.HTML)
            .body(CreateContainerServicePage.failure(workstation, result));
      }
      return redirect(ServicePage.path(workstation.name(), result.name()));
    } catch (InvalidRegistrationException e) {
      return ResponseEntity.badRequest()
          .contentType(Layout.HTML)
          .body(form(target, null, fields, e));
    }
  }

  /** {@code requestedPort} is the port that the link of a container line chose, if any. */
  private String form(
      String target,
      String requestedPort,
      CreateContainerServicePage.Fields submitted,
      InvalidRegistrationException error) {
    List<ListedContainer> listed = List.of();
    String problem = null;
    try {
      listed = containers.containers();
    } catch (UnreadableDockerException e) {
      problem = e.getMessage();
    }
    List<ListedContainer> members =
        listed.stream().filter(c -> target(c).equals(target.strip())).toList();
    String proposed = members.isEmpty() ? "" : members.getFirst().service();
    List<CreateContainerServicePage.Port> ports = ports(members);
    String port =
        submitted != null
            ? submitted.target().port()
            : !blank(requestedPort)
                ? requestedPort.strip()
                : ports.size() == 1 ? String.valueOf(ports.getFirst().number()) : "";
    var fields =
        new CreateContainerServicePage.Fields(
            submitted == null ? proposed : submitted.name(),
            new CreateContainerServicePage.Target(target, port, ports),
            submitted == null || submitted.publish(),
            submitted == null ? "local" : submitted.scope());
    return CreateContainerServicePage.render(workstation, fields, problem, error);
  }

  private static List<CreateContainerServicePage.Port> ports(List<ListedContainer> members) {
    Map<Integer, Set<Integer>> ports = new TreeMap<>();
    for (ListedContainer member : members) {
      for (var port : member.ports()) {
        var hosts = ports.computeIfAbsent(port.containerPort(), ignored -> new TreeSet<>());
        if (port.hostPort() != null) {
          hosts.add(port.hostPort());
        }
      }
    }
    return ports.entrySet().stream()
        .map(p -> new CreateContainerServicePage.Port(p.getKey(), List.copyOf(p.getValue())))
        .toList();
  }

  private static String target(ListedContainer container) {
    return container.composeProject() == null
        ? container.name()
        : container.composeProject() + "/" + container.composeService();
  }

  private static boolean oneTarget(List<String> targets) {
    return targets != null && targets.size() == 1 && !targets.getFirst().isBlank();
  }

  private static ResponseEntity<String> invalidTarget() {
    return ResponseEntity.badRequest()
        .contentType(Layout.HTML)
        .body("Choose one container or Compose service.");
  }

  private static ResponseEntity<String> redirect(String path) {
    return ResponseEntity.status(HttpStatus.SEE_OTHER).location(URI.create(path)).build();
  }
}
