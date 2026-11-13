package cc.lvhy.yard.dashboard.web;

import cc.lvhy.yard.dashboard.services.InstancePriorityRequest;
import cc.lvhy.yard.dashboard.services.InvalidPriorityException;
import cc.lvhy.yard.dashboard.services.InvalidRegistrationException;
import cc.lvhy.yard.dashboard.services.PublicationRequest;
import cc.lvhy.yard.dashboard.services.Publications;
import cc.lvhy.yard.dashboard.services.Scope;
import cc.lvhy.yard.dashboard.services.Services;
import cc.lvhy.yard.dashboard.services.UnknownRouteProfileException;
import cc.lvhy.yard.dashboard.services.UnknownServiceException;
import cc.lvhy.yard.dashboard.services.YardReader;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

/** The Service page's Publication and Instance Priority controls. */
@Controller
class RoutingPagesController {

  private final YardReader yard;

  private final Publications publications;

  private final Services services;

  RoutingPagesController(YardReader yard, Publications publications, Services services) {
    this.yard = yard;
    this.publications = publications;
    this.services = services;
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

  /**
   * After a form is posted, the browser loads {@code path} with a GET, so a reload does not post
   * again.
   */
  private static ResponseEntity<String> seeOther(String path) {
    return ResponseEntity.status(HttpStatus.SEE_OTHER).location(URI.create(path)).build();
  }
}
