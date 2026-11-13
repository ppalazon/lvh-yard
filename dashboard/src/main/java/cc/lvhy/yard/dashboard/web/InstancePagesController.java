package cc.lvhy.yard.dashboard.web;

import cc.lvhy.yard.dashboard.Workstation;
import cc.lvhy.yard.dashboard.services.ContainerBindings;
import cc.lvhy.yard.dashboard.services.Instance;
import cc.lvhy.yard.dashboard.services.InvalidRegistrationException;
import cc.lvhy.yard.dashboard.services.Registrations;
import cc.lvhy.yard.dashboard.services.Service;
import cc.lvhy.yard.dashboard.services.UnknownContainerBindingException;
import cc.lvhy.yard.dashboard.services.UnknownInstanceException;
import cc.lvhy.yard.dashboard.services.UnknownServiceException;
import cc.lvhy.yard.dashboard.services.YardReader;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

/** The forms that edit Static Instances and remove Instance Sources. */
@Controller
class InstancePagesController {

  private final Workstation workstation;

  private final YardReader yard;

  private final Registrations registrations;

  private final ContainerBindings containerBindings;

  private final ServicePageAccess access;

  InstancePagesController(
      Workstation workstation,
      YardReader yard,
      Registrations registrations,
      ContainerBindings containerBindings,
      ServicePageAccess access) {
    this.workstation = workstation;
    this.yard = yard;
    this.registrations = registrations;
    this.containerBindings = containerBindings;
    this.access = access;
  }

  @GetMapping(
      path = "/services/{workstation}/{service}/instances/new",
      produces = MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  String newStaticInstance(@PathVariable("workstation") String home, @PathVariable String service) {
    return AddStaticInstancePage.render(
        workstation,
        access.ownService(home, service),
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
    Service found = access.ownService(home, service);
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
    Service found = access.ownService(home, service);
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
    Service found = access.ownService(home, service);
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
   * After a form is posted, the browser loads {@code path} with a GET, so a reload does not post
   * again.
   */
  private static ResponseEntity<String> seeOther(String path) {
    return ResponseEntity.status(HttpStatus.SEE_OTHER).location(URI.create(path)).build();
  }
}
