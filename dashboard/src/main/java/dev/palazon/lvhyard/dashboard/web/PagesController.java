package dev.palazon.lvhyard.dashboard.web;

import dev.palazon.lvhyard.dashboard.Workstation;
import dev.palazon.lvhyard.dashboard.profiles.RouteProfiles;
import dev.palazon.lvhyard.dashboard.reachability.ReachabilityTester;
import dev.palazon.lvhyard.dashboard.services.Alias;
import dev.palazon.lvhyard.dashboard.services.AliasExistsException;
import dev.palazon.lvhyard.dashboard.services.AliasTarget;
import dev.palazon.lvhyard.dashboard.services.Aliases;
import dev.palazon.lvhyard.dashboard.services.ContainerBindings;
import dev.palazon.lvhyard.dashboard.services.Instance;
import dev.palazon.lvhyard.dashboard.services.InstancePriorityRequest;
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
import dev.palazon.lvhyard.dashboard.services.UnknownAliasException;
import dev.palazon.lvhyard.dashboard.services.UnknownContainerBindingException;
import dev.palazon.lvhyard.dashboard.services.UnknownInstanceException;
import dev.palazon.lvhyard.dashboard.services.UnknownRouteProfileException;
import dev.palazon.lvhyard.dashboard.services.UnknownServiceException;
import dev.palazon.lvhyard.dashboard.services.UnknownStateException;
import dev.palazon.lvhyard.dashboard.services.UnknownWorkstationException;
import dev.palazon.lvhyard.dashboard.services.UnreachableWorkstationException;
import dev.palazon.lvhyard.dashboard.services.YardReader;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
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
      ReachabilityTester reachability,
      RouteProfiles routeProfiles,
      Services services) {
    this.workstation = workstation;
    this.yard = yard;
    this.registrations = registrations;
    this.publications = publications;
    this.aliases = aliases;
    this.containerBindings = containerBindings;
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

  /** A form that names a Service shows this at its name field. */
  private static InvalidRegistrationException notCreated(UnknownServiceException e) {
    return new InvalidRegistrationException(
        "name", e.getMessage() + " Create it first, with New Service in the catalog.");
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
    Service found = anyService(home, service);
    return ServicePage.render(workstation, found, aliasesOf(found), routeProfiles.names(), null);
  }

  /** Only this Workstation reads the Aliases of its own Services, and only of those you created. */
  private List<Alias> aliasesOf(Service service) {
    return workstation.is(service.workstation()) && service.created()
        ? aliases.of(service.name())
        : List.of();
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
    return ServicePage.render(
        workstation, found, aliasesOf(found), routeProfiles.names(), reachability.test(found));
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
      path = "/services/{workstation}/{service}/instances/new",
      produces = MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  String newStaticInstance(@PathVariable("workstation") String home, @PathVariable String service) {
    return AddStaticInstancePage.render(
        workstation,
        ownService(home, service),
        InstanceForm.Fields.EMPTY,
        registrations.defaultHost(),
        null);
  }

  @PostMapping(
      path = "/services/{workstation}/{service}/instances",
      produces = MediaType.TEXT_HTML_VALUE)
  ResponseEntity<String> addStaticInstance(
      @PathVariable("workstation") String home,
      @PathVariable String service,
      InstanceForm.Fields fields) {
    Service found = ownService(home, service);
    try {
      registrations.register(home, service, fields.request());
    } catch (InvalidRegistrationException error) {
      return ResponseEntity.badRequest()
          .contentType(Layout.HTML)
          .body(
              AddStaticInstancePage.render(
                  workstation, found, fields, registrations.defaultHost(), error));
    } catch (UnknownServiceException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
    }
    return seeOther(ServicePage.path(found));
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
   * With no Scope, a new Publication gets Local Scope, and an existing one keeps its own.
   *
   * @param profiles the Route Profiles in order, separated by commas. Blank removes them all.
   * @param accessLog true makes Traefik log the requests to the Route
   */
  @PostMapping("/services/{workstation}/{service}/publish")
  ResponseEntity<String> publish(
      @PathVariable("workstation") String home,
      @PathVariable String service,
      @RequestParam(required = false) String scope,
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
          home, service, new PublicationRequest(chosen, commaSeparated(profiles), accessLog));
    } catch (UnknownServiceException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
    } catch (InvalidRegistrationException | UnknownRouteProfileException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
    }
    return seeOther(ServicePage.path(home, service));
  }

  /**
   * @param order the Instance Source IDs in order, separated by commas. Left out, it keeps the
   *     order.
   * @param on left out, it keeps the toggle
   */
  @PostMapping("/services/{workstation}/{service}/instance-priority")
  ResponseEntity<String> instancePriority(
      @PathVariable("workstation") String home,
      @PathVariable String service,
      @RequestParam(required = false) String order,
      @RequestParam(required = false) Boolean on) {
    try {
      services.setInstancePriority(
          home,
          service,
          new InstancePriorityRequest(order == null ? null : commaSeparated(order), on));
    } catch (UnknownServiceException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
    } catch (InvalidPriorityException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
    }
    return seeOther(ServicePage.path(home, service));
  }

  /** Blank text names nothing, so it empties the list. It is not an unknown name. */
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

  @GetMapping(
      path = "/services/{workstation}/{service}/aliases/new",
      produces = MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  String newAlias(@PathVariable("workstation") String home, @PathVariable String service) {
    return AddAliasPage.render(
        workstation, ownService(home, service), AddAliasPage.shared(yard.routableYard()), "", null);
  }

  /**
   * Every refusal shows at the form's one field.
   *
   * @param target the Remote Service, as {@code <workstation>/<service>}
   */
  @PostMapping(
      path = "/services/{workstation}/{service}/aliases",
      produces = MediaType.TEXT_HTML_VALUE)
  ResponseEntity<String> addAlias(
      @PathVariable("workstation") String home,
      @PathVariable String service,
      @RequestParam(required = false) String target) {
    Service found = ownService(home, service);
    String chosen = target == null ? "" : target.strip();
    AliasTarget parsed = AliasTarget.parse(chosen);
    InvalidRegistrationException error;
    try {
      aliases.add(service, parsed.workstation(), parsed.service(), false);
      return seeOther(ServicePage.path(found));
    } catch (InvalidRegistrationException | AliasExistsException e) {
      error = new InvalidRegistrationException(AddAliasPage.TARGET, e.getMessage());
    } catch (UnknownServiceException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
    }
    return ResponseEntity.badRequest()
        .contentType(Layout.HTML)
        .body(
            AddAliasPage.render(
                workstation, found, AddAliasPage.shared(yard.routableYard()), chosen, error));
  }

  @GetMapping(
      path = "/services/{workstation}/{service}/alias/new",
      produces = MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  String newAliasTo(@PathVariable("workstation") String home, @PathVariable String service) {
    Service remote = remoteService(home, service);
    return AliasRemoteServicePage.render(
        workstation, remote, ownServiceNames(), remote.name(), null);
  }

  /**
   * A new name creates the Service and publishes it with Local Scope, as {@code lvh-yard alias add}
   * does.
   *
   * @param name the Service of this Workstation that gets the Alias
   */
  @PostMapping(
      path = "/services/{workstation}/{service}/alias",
      produces = MediaType.TEXT_HTML_VALUE)
  ResponseEntity<String> aliasTo(
      @PathVariable("workstation") String home,
      @PathVariable String service,
      @RequestParam(required = false) String name) {
    Service remote = remoteService(home, service);
    String chosen = name == null ? "" : name.strip();
    InvalidRegistrationException error;
    try {
      aliases.add(chosen, home, service, true);
      return seeOther(ServicePage.path(workstation.name(), chosen));
    } catch (InvalidRegistrationException e) {
      error =
          e.field().equals("name")
              ? e
              : new InvalidRegistrationException(AliasRemoteServicePage.TARGET, e.getMessage());
    } catch (AliasExistsException e) {
      error = new InvalidRegistrationException(AliasRemoteServicePage.NAME, e.getMessage());
    }
    return ResponseEntity.badRequest()
        .contentType(Layout.HTML)
        .body(AliasRemoteServicePage.render(workstation, remote, ownServiceNames(), name, error));
  }

  private Service remoteService(String home, String service) {
    if (workstation.is(home)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
    return anyService(home, service);
  }

  private List<String> ownServiceNames() {
    return yard.services(workstation.name()).stream().map(Service::name).toList();
  }

  @PostMapping("/services/{workstation}/{service}/aliases/{remote}/{remoteService}/remove")
  ResponseEntity<String> removeAlias(
      @PathVariable("workstation") String home,
      @PathVariable String service,
      @PathVariable String remote,
      @PathVariable String remoteService) {
    ownService(home, service);
    try {
      aliases.remove(service, remote, remoteService);
    } catch (UnknownAliasException | UnknownServiceException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
    }
    return seeOther(ServicePage.path(home, service));
  }

  /**
   * After a form is posted, the browser loads {@code path} with a GET, so a reload does not post
   * again.
   */
  private static ResponseEntity<String> seeOther(String path) {
    return ResponseEntity.status(HttpStatus.SEE_OTHER).location(URI.create(path)).build();
  }
}
