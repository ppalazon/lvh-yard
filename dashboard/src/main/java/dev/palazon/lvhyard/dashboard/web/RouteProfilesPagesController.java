package dev.palazon.lvhyard.dashboard.web;

import dev.palazon.lvhyard.dashboard.Workstation;
import dev.palazon.lvhyard.dashboard.profiles.NoRouteProfileException;
import dev.palazon.lvhyard.dashboard.profiles.RefusedRouteProfilesException;
import dev.palazon.lvhyard.dashboard.profiles.RouteProfileChecks;
import dev.palazon.lvhyard.dashboard.profiles.RouteProfileDetail;
import dev.palazon.lvhyard.dashboard.profiles.RouteProfileEditor;
import dev.palazon.lvhyard.dashboard.profiles.RouteProfileInUseException;
import java.net.URI;
import java.util.List;
import java.util.Map;
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

/** The Route Profiles page. It only translates to views. */
@Controller
class RouteProfilesPagesController {

  private final Workstation workstation;

  private final RouteProfileEditor profiles;

  RouteProfilesPagesController(Workstation workstation, RouteProfileEditor profiles) {
    this.workstation = workstation;
    this.profiles = profiles;
  }

  @GetMapping(path = RouteProfilesPage.PATH, produces = MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  String routeProfiles(@RequestParam(required = false) String example) {
    if (example == null) {
      return page(RouteProfilesPage.Form.EMPTY);
    }
    RouteProfileExample chosen =
        RouteProfileExample.of(example)
            .orElseThrow(() ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "No example is called " + example + "."));
    return page(new RouteProfilesPage.Form(chosen.profile, chosen.text, List.of()));
  }

  /** Adds the profile, or replaces the one of the same name. */
  @PostMapping(path = RouteProfilesPage.PATH, produces = MediaType.TEXT_HTML_VALUE)
  ResponseEntity<String> save(@RequestParam String name, @RequestParam String definition) {
    String profile = name.strip();
    List<String> errors;
    if (profile.isEmpty()) {
      errors = List.of("A Route Profile needs a name.");
    } else {
      try {
        profiles.save(Map.of(profile, RouteProfileYaml.read(definition)));
        return seeOther(RouteProfilesPage.PATH);
      } catch (IllegalArgumentException e) {
        errors = List.of(e.getMessage());
      } catch (RefusedRouteProfilesException e) {
        errors = e.problems().stream().map(RouteProfileChecks.Problem::reason).toList();
      }
    }
    return ResponseEntity.badRequest()
        .contentType(Layout.HTML)
        .body(page(new RouteProfilesPage.Form(name, definition, errors)));
  }

  @GetMapping(
      path = RouteProfilesPage.PATH + "/{profile}/edit",
      produces = MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  String editForm(@PathVariable String profile) {
    RouteProfileDetail found;
    try {
      found = profiles.find(profile);
    } catch (NoRouteProfileException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
    }
    return page(
        new RouteProfilesPage.Form(
            found.name(), RouteProfileYaml.write(found.definition()), List.of()));
  }

  @PostMapping(
      path = RouteProfilesPage.PATH + "/{profile}/remove",
      produces = MediaType.TEXT_HTML_VALUE)
  ResponseEntity<String> remove(@PathVariable String profile) {
    try {
      profiles.remove(profile);
    } catch (NoRouteProfileException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
    } catch (RouteProfileInUseException e) {
      return ResponseEntity.status(HttpStatus.CONFLICT)
          .contentType(Layout.HTML)
          .body(page(RouteProfilesPage.Form.EMPTY, e.getMessage()));
    }
    return seeOther(RouteProfilesPage.PATH);
  }

  private String page(RouteProfilesPage.Form form) {
    return page(form, null);
  }

  private String page(RouteProfilesPage.Form form, String removeError) {
    return RouteProfilesPage.render(workstation, profiles.list(), form, removeError);
  }

  /**
   * After a form is posted, the browser loads {@code path} with a GET, so a reload does not post
   * again.
   */
  private static ResponseEntity<String> seeOther(String path) {
    return ResponseEntity.status(HttpStatus.SEE_OTHER).location(URI.create(path)).build();
  }
}
