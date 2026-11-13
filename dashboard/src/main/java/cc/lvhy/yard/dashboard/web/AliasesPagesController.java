package cc.lvhy.yard.dashboard.web;

import cc.lvhy.yard.dashboard.Workstation;
import cc.lvhy.yard.dashboard.services.AliasTarget;
import cc.lvhy.yard.dashboard.services.Aliases;
import cc.lvhy.yard.dashboard.services.InvalidRegistrationException;
import cc.lvhy.yard.dashboard.services.Service;
import cc.lvhy.yard.dashboard.services.UnknownAliasException;
import cc.lvhy.yard.dashboard.services.UnknownServiceException;
import cc.lvhy.yard.dashboard.services.YardReader;
import java.net.URI;
import java.util.List;
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

/** The forms that add and remove Aliases. */
@Controller
class AliasesPagesController {

  private final Workstation workstation;

  private final YardReader yard;

  private final Aliases aliases;

  private final ServicePageAccess access;

  AliasesPagesController(
      Workstation workstation, YardReader yard, Aliases aliases, ServicePageAccess access) {
    this.workstation = workstation;
    this.yard = yard;
    this.aliases = aliases;
    this.access = access;
  }

  @GetMapping(
      path = "/services/{workstation}/{service}/aliases/new",
      produces = MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  String newAlias(@PathVariable("workstation") String home, @PathVariable String service) {
    return AddAliasPage.render(
        workstation,
        access.ownService(home, service),
        AddAliasPage.shared(yard.routableYard()),
        "",
        null);
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
    Service found = access.ownService(home, service);
    String chosen = target == null ? "" : target.strip();
    AliasTarget parsed = AliasTarget.parse(chosen);
    InvalidRegistrationException error;
    try {
      aliases.add(service, parsed.workstation(), parsed.service(), false);
      return seeOther(ServicePage.path(found));
    } catch (InvalidRegistrationException e) {
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
   * does. For an existing Service, the page opens at its Instance Sources, so you see the Alias
   * join them.
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
      boolean created = aliases.add(chosen, home, service, true).serviceCreated();
      String page = ServicePage.path(workstation.name(), chosen);
      return seeOther(created ? page : page + "#" + InstanceSourcesCard.ID);
    } catch (InvalidRegistrationException e) {
      error =
          e.field().equals("name")
              ? e
              : new InvalidRegistrationException(AliasRemoteServicePage.TARGET, e.getMessage());
    }
    return ResponseEntity.badRequest()
        .contentType(Layout.HTML)
        .body(AliasRemoteServicePage.render(workstation, remote, ownServiceNames(), name, error));
  }

  private Service remoteService(String home, String service) {
    if (workstation.is(home)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
    return access.anyService(home, service);
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
    access.ownService(home, service);
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
