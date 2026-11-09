package dev.palazon.lvhyard.dashboard.web;

import dev.palazon.lvhyard.dashboard.Workstation;
import dev.palazon.lvhyard.dashboard.profiles.RouteProfiles;
import dev.palazon.lvhyard.dashboard.reachability.ReachabilityTester;
import dev.palazon.lvhyard.dashboard.services.Aliases;
import dev.palazon.lvhyard.dashboard.services.ContainerBindingRequest;
import dev.palazon.lvhyard.dashboard.services.ContainerBindings;
import dev.palazon.lvhyard.dashboard.services.ContainerRegistration;
import dev.palazon.lvhyard.dashboard.services.ContainerRegistrationRequest;
import dev.palazon.lvhyard.dashboard.services.FollowedContainerException;
import dev.palazon.lvhyard.dashboard.services.Instance;
import dev.palazon.lvhyard.dashboard.services.InvalidPriorityException;
import dev.palazon.lvhyard.dashboard.services.InvalidRegistrationException;
import dev.palazon.lvhyard.dashboard.services.PublicationRequest;
import dev.palazon.lvhyard.dashboard.services.Publications;
import dev.palazon.lvhyard.dashboard.services.Registrations;
import dev.palazon.lvhyard.dashboard.services.RemoteServiceException;
import dev.palazon.lvhyard.dashboard.services.Scope;
import dev.palazon.lvhyard.dashboard.services.Service;
import dev.palazon.lvhyard.dashboard.services.ServiceExistsException;
import dev.palazon.lvhyard.dashboard.services.ServiceState;
import dev.palazon.lvhyard.dashboard.services.Services;
import dev.palazon.lvhyard.dashboard.services.UnfollowedContainer;
import dev.palazon.lvhyard.dashboard.services.UnknownAliasException;
import dev.palazon.lvhyard.dashboard.services.UnknownContainerBindingException;
import dev.palazon.lvhyard.dashboard.services.UnknownInstanceException;
import dev.palazon.lvhyard.dashboard.services.UnknownRouteProfileException;
import dev.palazon.lvhyard.dashboard.services.UnknownServiceException;
import dev.palazon.lvhyard.dashboard.services.UnknownStateException;
import dev.palazon.lvhyard.dashboard.services.UnknownWorkstationException;
import dev.palazon.lvhyard.dashboard.services.UnreachableWorkstationException;
import dev.palazon.lvhyard.dashboard.services.UnreadableDockerException;
import dev.palazon.lvhyard.dashboard.services.Yard;
import dev.palazon.lvhyard.dashboard.services.YardReader;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

/** The HTML pages. They only translate to views. */
@Controller
class PagesController {

  private final Workstation workstation;

  private final YardReader yard;

  private final Registrations registrations;

  private final Publications publications;

  private final Aliases aliases;

  private final ContainerBindings containerBindings;

  private final ContainerRegistration containerRegistration;

  private final ReachabilityTester reachability;

  private final RouteProfiles routeProfiles;

  private final Services services;

  PagesController(
      Workstation workstation,
      YardReader yard,
      Registrations registrations,
      Publications publications,
      Aliases aliases,
      ContainerBindings containerBindings,
      ContainerRegistration containerRegistration,
      ReachabilityTester reachability,
      RouteProfiles routeProfiles,
      Services services) {
    this.workstation = workstation;
    this.yard = yard;
    this.registrations = registrations;
    this.publications = publications;
    this.aliases = aliases;
    this.containerBindings = containerBindings;
    this.containerRegistration = containerRegistration;
    this.reachability = reachability;
    this.routeProfiles = routeProfiles;
    this.services = services;
  }

  @GetMapping(path = "/", produces = MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  String catalog(
      @RequestParam(required = false) String workstation,
      @RequestParam(required = false) String state) {
    ServiceState only;
    try {
      only = blank(state) ? null : ServiceState.parse(state);
    } catch (UnknownStateException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
    }
    return CatalogPage.render(
        this.workstation,
        yard.yard(),
        new CatalogPage.Filters(blank(workstation) ? null : workstation, only));
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }

  /** Keeps old bookmarks working. */
  @GetMapping(StaticInstancesPage.OLD_PATH)
  ResponseEntity<Void> register() {
    return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY)
        .location(URI.create(StaticInstancesPage.PATH))
        .build();
  }

  @GetMapping(path = StaticInstancesPage.PATH, produces = MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  String staticInstances() {
    return StaticInstancesPage.render(workstation, yard.services(workstation.name()));
  }

  @GetMapping(path = StaticInstancesPage.NEW_PATH, produces = MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  String newStaticInstance() {
    return newStaticInstancePage("", InstanceForm.Fields.EMPTY, null);
  }

  @PostMapping(path = StaticInstancesPage.PATH, produces = MediaType.TEXT_HTML_VALUE)
  ResponseEntity<String> registerStaticInstance(
      @RequestParam String name, InstanceForm.Fields fields) {
    String service = name.strip();
    InvalidRegistrationException error;
    try {
      registrations.register(workstation.name(), service, fields.request());
      return seeOther(ServicePage.path(workstation.name(), service));
    } catch (InvalidRegistrationException e) {
      error = e;
    } catch (UnknownServiceException e) {
      error = notCreated(e);
    }
    return ResponseEntity.badRequest()
        .contentType(Layout.HTML)
        .body(newStaticInstancePage(name, fields, error));
  }

  /** A form that names a Service shows this at its name field. */
  private static InvalidRegistrationException notCreated(UnknownServiceException e) {
    return new InvalidRegistrationException(
        "name", e.getMessage() + " Create it first, with New Service in the catalog.");
  }

  private String newStaticInstancePage(
      String name, InstanceForm.Fields fields, InvalidRegistrationException error) {
    return StaticInstancesPage.renderNew(
        workstation, name, fields, registrations.defaultHost(), error);
  }

  @GetMapping(path = NewServicePage.PATH, produces = MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  String newService() {
    return NewServicePage.render(workstation, "", null);
  }

  /** A name that breaks a rule or is in use shows at the name field. */
  @PostMapping(path = NewServicePage.ACTION, produces = MediaType.TEXT_HTML_VALUE)
  ResponseEntity<String> createService(@RequestParam(required = false) String name) {
    String service = name == null ? "" : name.strip();
    InvalidRegistrationException error;
    try {
      services.create(workstation.name(), service);
      return seeOther(ServicePage.path(workstation.name(), service));
    } catch (InvalidRegistrationException e) {
      error = e;
    } catch (ServiceExistsException e) {
      error = new InvalidRegistrationException("name", e.getMessage());
    }
    return ResponseEntity.badRequest()
        .contentType(Layout.HTML)
        .body(NewServicePage.render(workstation, name, error));
  }

  @GetMapping(path = "/services/{workstation}/{service}", produces = MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  String service(@PathVariable("workstation") String home, @PathVariable String service) {
    return ServicePage.render(workstation, anyService(home, service), routeProfiles.names(), null);
  }

  @GetMapping(
      path = "/services/{workstation}/{service}/delete",
      produces = MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  String confirmDeleteService(
      @PathVariable("workstation") String home, @PathVariable String service) {
    return DeleteServicePage.render(workstation, anyService(home, service));
  }

  /** The Catalog, or the Service page while Foreign Instances keep the Service. */
  @PostMapping("/services/{workstation}/{service}/delete")
  ResponseEntity<String> deleteService(
      @PathVariable("workstation") String home, @PathVariable String service) {
    try {
      services.delete(home, service);
    } catch (UnknownServiceException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
    }
    return seeOther(yard.service(home, service).map(ServicePage::path).orElse("/"));
  }

  /** The results are not kept. */
  @GetMapping(
      path = "/services/{workstation}/{service}/reachability",
      produces = MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  String reachability(@PathVariable("workstation") String home, @PathVariable String service) {
    Service found = anyService(home, service);
    return ServicePage.render(workstation, found, routeProfiles.names(), reachability.test(found));
  }

  private Service anyService(String home, String service) {
    try {
      return yard.service(home, service)
          .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    } catch (UnknownWorkstationException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
    } catch (UnreachableWorkstationException e) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage(), e);
    }
  }

  @GetMapping(
      path = "/services/{workstation}/{service}/instances/{id}/edit",
      produces = MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  String editForm(
      @PathVariable("workstation") String home,
      @PathVariable String service,
      @PathVariable String id) {
    Service found = ownService(home, service);
    Instance instance = instance(found, id);
    return EditInstancePage.render(
        workstation,
        found,
        instance,
        InstanceForm.Fields.of(instance),
        registrations.defaultHost(),
        null);
  }

  @PostMapping(
      path = "/services/{workstation}/{service}/instances/{id}/edit",
      produces = MediaType.TEXT_HTML_VALUE)
  ResponseEntity<String> edit(
      @PathVariable("workstation") String home,
      @PathVariable String service,
      @PathVariable String id,
      InstanceForm.Fields fields) {
    Service found = ownService(home, service);
    Instance instance = instance(found, id);
    try {
      registrations.edit(home, service, id, fields.request());
    } catch (InvalidRegistrationException error) {
      return ResponseEntity.badRequest()
          .contentType(Layout.HTML)
          .body(
              EditInstancePage.render(
                  workstation, found, instance, fields, registrations.defaultHost(), error));
    } catch (UnknownInstanceException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
    }
    return seeOther(ServicePage.path(found));
  }

  @ExceptionHandler
  void forbidden(RemoteServiceException e, HttpServletResponse response) throws IOException {
    response.sendError(HttpStatus.FORBIDDEN.value(), e.getMessage());
  }

  /** A Remote Service's Instances cannot be edited, so it is forbidden. */
  private Service ownService(String home, String service) {
    if (!workstation.is(home)) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "The Services of " + home + " can be edited only on " + home + ".");
    }
    return yard.service(home, service)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
  }

  private static Instance instance(Service service, String id) {
    return service.instances().stream()
        .filter(instance -> instance.id().equals(id))
        .findFirst()
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
  }

  /** Shows the Catalog when the Service is gone with it. */
  @PostMapping("/services/{workstation}/{service}/instances/{id}/remove")
  ResponseEntity<String> remove(
      @PathVariable("workstation") String home,
      @PathVariable String service,
      @PathVariable String id) {
    try {
      registrations.remove(home, service, id);
    } catch (UnknownInstanceException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
    }
    return seeOther(yard.service(home, service).map(ServicePage::path).orElse("/"));
  }

  /** Shows the Catalog when the Service is gone with it. */
  @PostMapping("/services/{workstation}/{service}/container-bindings/{id}/remove")
  ResponseEntity<String> removeContainerBinding(
      @PathVariable("workstation") String home,
      @PathVariable String service,
      @PathVariable String id) {
    try {
      containerBindings.remove(home, service, id);
    } catch (UnknownContainerBindingException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
    }
    return seeOther(yard.service(home, service).map(ServicePage::path).orElse("/"));
  }

  /**
   * With no Scope or no priority, a new Publication gets Local Scope or the default order, and an
   * existing one keeps its own.
   *
   * @param priority the kinds in order, separated by commas. Blank turns the Instance Priority off.
   * @param profiles the Route Profiles in order, separated by commas. Blank removes them all.
   * @param accessLog true makes Traefik log the requests to the Route
   */
  @PostMapping("/services/{workstation}/{service}/publish")
  ResponseEntity<String> publish(
      @PathVariable("workstation") String home,
      @PathVariable String service,
      @RequestParam(required = false) String scope,
      @RequestParam(required = false) String priority,
      @RequestParam(required = false) String profiles,
      @RequestParam(required = false) Boolean accessLog) {
    Scope chosen =
        scope == null
            ? null
            : Scope.named(scope)
                .orElseThrow(() ->
                    new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "The Scope is local or yard, not " + scope + "."));
    try {
      publications.publish(
          home,
          service,
          new PublicationRequest(
              chosen, commaSeparated(priority), commaSeparated(profiles), accessLog));
    } catch (UnknownServiceException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
    } catch (InvalidRegistrationException
        | InvalidPriorityException
        | UnknownRouteProfileException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
    }
    return seeOther(ServicePage.path(home, service));
  }

  /**
   * Blank text names nothing, so it empties the list, such as turning the Instance Priority off. It
   * is not an unknown name.
   */
  private static List<String> commaSeparated(String text) {
    if (text == null) {
      return null;
    }
    return text.isBlank()
        ? List.of()
        : Arrays.stream(text.split(",", -1)).map(String::strip).toList();
  }

  /** Shows the Catalog when the Service had no Instances left. */
  @PostMapping("/services/{workstation}/{service}/unpublish")
  ResponseEntity<String> unpublish(
      @PathVariable("workstation") String home, @PathVariable String service) {
    try {
      publications.unpublish(home, service);
    } catch (UnknownServiceException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
    }
    return seeOther(yard.service(home, service).map(ServicePage::path).orElse("/"));
  }

  /** Docker that cannot be read is told on the page, which the refresh can only swap from a 200. */
  @GetMapping(path = ContainersPage.PATH, produces = MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  String containers(@RequestParam(required = false) String registered) {
    return containersPage(
        registered == null ? null : yard.service(workstation.name(), registered).orElse(null),
        null);
  }

  @GetMapping(path = ContainersPage.NEW_PATH, produces = MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  String newContainerBinding() {
    return ContainersPage.renderNew(workstation, ContainersPage.BindForm.EMPTY);
  }

  /**
   * @param registered the Service that the one-click Register just gave a container, or {@code
   *     null}
   */
  private String containersPage(Service registered, ContainersPage.RegisterFailure failure) {
    try {
      return ContainersPage.render(
          workstation, containerRegistration.unfollowed(), null, registered, failure);
    } catch (UnreadableDockerException e) {
      return ContainersPage.render(workstation, List.of(), e.getMessage(), registered, failure);
    }
  }

  /**
   * The binding form of the Containers page. A followed target is shown at its field, as the other
   * failures are. With no Service name, the Service name is the one the register endpoint proposes.
   */
  @PostMapping(path = ContainersPage.BIND_PATH, produces = MediaType.TEXT_HTML_VALUE)
  ResponseEntity<String> bindContainer(ContainersPage.Binding binding) {
    InvalidRegistrationException error;
    try {
      ContainerBindingRequest request = binding.request();
      String service =
          binding.name().isBlank() && request.proposedService() != null
              ? request.proposedService()
              : binding.name().strip();
      containerBindings.add(workstation.name(), service, request);
      return seeOther(ServicePage.path(workstation.name(), service));
    } catch (InvalidRegistrationException e) {
      error = e;
    } catch (FollowedContainerException e) {
      error = new InvalidRegistrationException(e.field(), e.getMessage());
    } catch (UnknownServiceException e) {
      error = notCreated(e);
    }
    return ResponseEntity.badRequest()
        .contentType(Layout.HTML)
        .body(ContainersPage.renderNew(workstation, new ContainersPage.BindForm(binding, error)));
  }

  @GetMapping(
      path = ContainersPage.PATH + "/{container}/register",
      produces = MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  String registerForm(@PathVariable String container) {
    List<UnfollowedContainer> unfollowed = unfollowed(container);
    return ContainersPage.renderRegister(
        workstation, unfollowed, unfollowed.getFirst().service(), null, null);
  }

  /**
   * The register form shows the Service it gave the container, or why it failed at its field. The
   * one-click Register of the list goes back to the list, which says what it registered, or shows
   * why it failed in the container's row.
   *
   * @param name the Service name, sent only by the register form
   */
  @PostMapping(ContainersPage.PATH + "/{container}/register")
  ResponseEntity<String> registerContainer(
      @PathVariable String container,
      @RequestParam(required = false) String name,
      @RequestParam(required = false) String port) {
    boolean fromForm = name != null;
    Service service;
    InvalidRegistrationException error;
    try {
      service =
          containerRegistration.register(
              container, new ContainerRegistrationRequest(Registrations.parsePort(port), name));
      return seeOther(
          fromForm
              ? ServicePage.path(service)
              : ContainersPage.PATH + "?registered=" + service.name());
    } catch (InvalidRegistrationException e) {
      error = e;
    } catch (UnknownServiceException e) {
      error = notCreated(e);
    } catch (FollowedContainerException e) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage(), e);
    } catch (UnreadableDockerException e) {
      throw dockerUnavailable(e);
    }
    return ResponseEntity.badRequest()
        .contentType(Layout.HTML)
        .body(
            fromForm
                ? ContainersPage.renderRegister(
                    workstation, unfollowed(container), name, port == null ? "" : port, error)
                : containersPage(
                    null, new ContainersPage.RegisterFailure(container, error.getMessage())));
  }

  private List<UnfollowedContainer> unfollowed(String name) {
    List<UnfollowedContainer> containers;
    try {
      containers = containerRegistration.unfollowed();
    } catch (UnreadableDockerException e) {
      throw dockerUnavailable(e);
    }
    UnfollowedContainer target =
        containers.stream()
            .filter(container -> container.name().equals(name))
            .findFirst()
            .orElseThrow(() ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "No running container that LVH Yard does not follow is called " + name + "."));
    if (target.composeProject() == null) {
      return List.of(target);
    }
    List<UnfollowedContainer> members = new ArrayList<>();
    members.add(target);
    for (UnfollowedContainer container : containers) {
      if (!container.name().equals(name)
          && target.composeProject().equals(container.composeProject())
          && target.composeService().equals(container.composeService())) {
        members.add(container);
      }
    }
    return members;
  }

  private static ResponseStatusException dockerUnavailable(UnreadableDockerException e) {
    return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage(), e);
  }

  @GetMapping(path = AliasesPage.PATH, produces = MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  String aliases() {
    Yard read = yard.routableYard();
    return AliasesPage.render(workstation, read, aliases.of(read));
  }

  /** With no Remote Service to name, the Aliases page tells why no Alias can be added. */
  @GetMapping(path = AliasesPage.NEW_PATH, produces = MediaType.TEXT_HTML_VALUE)
  ResponseEntity<String> newAlias() {
    List<Service> shared = AliasesPage.shared(yard.routableYard());
    if (shared.isEmpty()) {
      return seeOther(AliasesPage.PATH);
    }
    return ResponseEntity.ok()
        .contentType(Layout.HTML)
        .body(AliasesPage.renderNew(workstation, shared, "", "", null));
  }

  @PostMapping(path = AliasesPage.PATH, produces = MediaType.TEXT_HTML_VALUE)
  ResponseEntity<String> addAlias(@RequestParam String name, @RequestParam String target) {
    int slash = target.indexOf('/');
    if (slash < 0) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "The Remote Service is <workstation>/<service>, not " + target + ".");
    }
    try {
      aliases.add(name.strip(), target.substring(0, slash), target.substring(slash + 1));
    } catch (InvalidRegistrationException error) {
      return ResponseEntity.badRequest()
          .contentType(Layout.HTML)
          .body(
              AliasesPage.renderNew(
                  workstation, AliasesPage.shared(yard.routableYard()), name, target, error));
    }
    return seeOther(AliasesPage.PATH);
  }

  @PostMapping(AliasesPage.PATH + "/{alias}/remove")
  ResponseEntity<String> removeAlias(@PathVariable String alias) {
    try {
      aliases.remove(alias);
    } catch (UnknownAliasException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
    }
    return seeOther(AliasesPage.PATH);
  }

  /**
   * After a form is posted, the browser loads {@code path} with a GET, so a reload does not post
   * again.
   */
  private static ResponseEntity<String> seeOther(String path) {
    return ResponseEntity.status(HttpStatus.SEE_OTHER).location(URI.create(path)).build();
  }
}
