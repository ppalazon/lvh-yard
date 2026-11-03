package dev.palazon.lvhyard.dashboard.web;

import dev.palazon.lvhyard.dashboard.Workstation;
import dev.palazon.lvhyard.dashboard.reachability.ReachabilityTester;
import dev.palazon.lvhyard.dashboard.services.Aliases;
import dev.palazon.lvhyard.dashboard.services.ContainerBindings;
import dev.palazon.lvhyard.dashboard.services.CoreServiceException;
import dev.palazon.lvhyard.dashboard.services.Instance;
import dev.palazon.lvhyard.dashboard.services.InvalidPriorityException;
import dev.palazon.lvhyard.dashboard.services.InvalidRegistrationException;
import dev.palazon.lvhyard.dashboard.services.Publications;
import dev.palazon.lvhyard.dashboard.services.Registrations;
import dev.palazon.lvhyard.dashboard.services.RemoteServiceException;
import dev.palazon.lvhyard.dashboard.services.Scope;
import dev.palazon.lvhyard.dashboard.services.Service;
import dev.palazon.lvhyard.dashboard.services.ServiceState;
import dev.palazon.lvhyard.dashboard.services.UnknownAliasException;
import dev.palazon.lvhyard.dashboard.services.UnknownContainerBindingException;
import dev.palazon.lvhyard.dashboard.services.UnknownInstanceException;
import dev.palazon.lvhyard.dashboard.services.UnknownServiceException;
import dev.palazon.lvhyard.dashboard.services.UnknownStateException;
import dev.palazon.lvhyard.dashboard.services.UnknownWorkstationException;
import dev.palazon.lvhyard.dashboard.services.UnreachableWorkstationException;
import dev.palazon.lvhyard.dashboard.services.Yard;
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

  PagesController(
      Workstation workstation,
      YardReader yard,
      Registrations registrations,
      Publications publications,
      Aliases aliases,
      ContainerBindings containerBindings,
      ReachabilityTester reachability) {
    this.workstation = workstation;
    this.yard = yard;
    this.registrations = registrations;
    this.publications = publications;
    this.aliases = aliases;
    this.containerBindings = containerBindings;
    this.reachability = reachability;
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

  @GetMapping(path = RegisterPage.PATH, produces = MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  String registerForm() {
    return RegisterPage.render(
        workstation,
        "",
        RegisterPage.Choice.EMPTY,
        InstanceForm.Fields.EMPTY,
        registrations.defaultHost(),
        null);
  }

  @PostMapping(path = RegisterPage.PATH, produces = MediaType.TEXT_HTML_VALUE)
  ResponseEntity<String> register(
      @RequestParam String name, RegisterPage.Choice choice, InstanceForm.Fields fields) {
    try {
      if (choice.containerBinding()) {
        containerBindings.add(workstation.name(), name.strip(), choice.request());
      } else {
        registrations.register(workstation.name(), name.strip(), fields.request());
      }
    } catch (InvalidRegistrationException error) {
      return ResponseEntity.badRequest()
          .contentType(Layout.HTML)
          .body(
              RegisterPage.render(
                  workstation, name, choice, fields, registrations.defaultHost(), error));
    }
    return seeOther(ServicePage.path(workstation.name(), name.strip()));
  }

  @GetMapping(path = "/services/{workstation}/{service}", produces = MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  String service(@PathVariable("workstation") String home, @PathVariable String service) {
    return ServicePage.render(workstation, anyService(home, service));
  }

  /** The results are not kept. */
  @GetMapping(
      path = "/services/{workstation}/{service}/reachability",
      produces = MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  String reachability(@PathVariable("workstation") String home, @PathVariable String service) {
    Service found = anyService(home, service);
    return ServicePage.render(workstation, found, reachability.test(found));
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
  void refused(CoreServiceException e, HttpServletResponse response) throws IOException {
    response.sendError(HttpStatus.CONFLICT.value(), e.getMessage());
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
   * @param priority the kinds in order, separated by commas
   */
  @PostMapping("/services/{workstation}/{service}/publish")
  ResponseEntity<String> publish(
      @PathVariable("workstation") String home,
      @PathVariable String service,
      @RequestParam(required = false) String scope,
      @RequestParam(required = false) String priority) {
    Scope chosen =
        scope == null
            ? null
            : Scope.named(scope)
                .orElseThrow(() ->
                    new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "The Scope is local or yard, not " + scope + "."));
    try {
      publications.publish(home, service, chosen, kinds(priority));
    } catch (UnknownServiceException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
    } catch (InvalidRegistrationException | InvalidPriorityException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
    }
    return seeOther(ServicePage.path(home, service));
  }

  /** Blank text names no kind, so it is refused as an empty order, not as an unknown kind. */
  private static List<String> kinds(String priority) {
    if (priority == null) {
      return null;
    }
    return priority.isBlank()
        ? List.of()
        : Arrays.stream(priority.split(",", -1)).map(String::strip).toList();
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

  @GetMapping(path = AliasesPage.PATH, produces = MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  String aliases() {
    return aliasesPage("", "", null);
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
          .body(aliasesPage(name, target, error));
    } catch (UnknownWorkstationException | UnknownServiceException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
    } catch (UnreachableWorkstationException e) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage(), e);
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

  private String aliasesPage(String name, String target, InvalidRegistrationException error) {
    Yard read = yard.routableYard();
    return AliasesPage.render(workstation, read, aliases.of(read), name, target, error);
  }

  /**
   * After a form is posted, the browser loads {@code path} with a GET, so a reload does not post
   * again.
   */
  private static ResponseEntity<String> seeOther(String path) {
    return ResponseEntity.status(HttpStatus.SEE_OTHER).location(URI.create(path)).build();
  }
}
