package cc.lvhy.yard.dashboard.web;

import cc.lvhy.yard.dashboard.Workstation;
import cc.lvhy.yard.dashboard.services.InvalidRegistrationException;
import cc.lvhy.yard.dashboard.services.Services;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

/** The forms that create and delete Services. */
@Controller
class ServiceLifecyclePagesController {

  private final Workstation workstation;

  private final YardReader yard;

  private final Services services;

  private final ServicePageAccess access;

  ServiceLifecyclePagesController(
      Workstation workstation, YardReader yard, Services services, ServicePageAccess access) {
    this.workstation = workstation;
    this.yard = yard;
    this.services = services;
    this.access = access;
  }

  @GetMapping(path = NewServicePage.PATH, produces = MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  String newService() {
    return NewServicePage.render(workstation, "", null);
  }

  /** A name that breaks a rule shows at the name field. A Service that exists opens its page. */
  @PostMapping(path = NewServicePage.ACTION, produces = MediaType.TEXT_HTML_VALUE)
  ResponseEntity<String> createService(@RequestParam(required = false) String name) {
    String service = name == null ? "" : name.strip();
    try {
      services.create(workstation.name(), service);
      return seeOther(ServicePage.path(workstation.name(), service));
    } catch (InvalidRegistrationException e) {
      return ResponseEntity.badRequest()
          .contentType(Layout.HTML)
          .body(NewServicePage.render(workstation, name, e));
    }
  }

  @GetMapping(
      path = "/services/{workstation}/{service}/delete",
      produces = MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  String confirmDeleteService(
      @PathVariable("workstation") String home, @PathVariable String service) {
    return DeleteServicePage.render(workstation, access.anyService(home, service));
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

  /**
   * After a form is posted, the browser loads {@code path} with a GET, so a reload does not post
   * again.
   */
  private static ResponseEntity<String> seeOther(String path) {
    return ResponseEntity.status(HttpStatus.SEE_OTHER).location(URI.create(path)).build();
  }
}
