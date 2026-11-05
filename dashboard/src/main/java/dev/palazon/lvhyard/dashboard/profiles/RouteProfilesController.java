package dev.palazon.lvhyard.dashboard.profiles;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The Route Profiles change only on their Home Workstation, so the path does not name it. */
@RestController
@RequestMapping(
    path = "/api/workstation/route-profiles",
    produces = MediaType.APPLICATION_JSON_VALUE)
class RouteProfilesController {

  private static final String REFUSED =
      "A profile is refused, or the save writes more profiles than Consul takes at once, and"
          + " nothing changes. The detail gives the reason, and the problems give each refused"
          + " profile";

  private final RouteProfileEditor editor;

  RouteProfilesController(RouteProfileEditor editor) {
    this.editor = editor;
  }

  @GetMapping
  @Operation(
      operationId = "routeProfiles",
      summary =
          "Lists this Workstation's Route Profiles, with their types and the Services that"
              + " use them")
  List<RouteProfile> routeProfiles() {
    return editor.list();
  }

  @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      operationId = "putRouteProfiles",
      summary = "Adds or replaces a set of Route Profiles",
      description =
          "The body maps each name to its middleware in Traefik's format. A chain may name a"
              + " profile of the set or one that exists. The profiles that the body does not name"
              + " stay. Answers the whole list.")
  @ApiResponse(responseCode = "200", description = "Saved", useReturnTypeSchema = true)
  @ApiResponse(responseCode = "400", description = REFUSED)
  List<RouteProfile> putRouteProfiles(@RequestBody Map<String, Object> profiles) {
    editor.save(profiles);
    return editor.list();
  }

  @GetMapping("/{name}")
  @Operation(
      operationId = "routeProfile",
      summary = "Gets one Route Profile, with its definition in Traefik's format")
  @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true)
  @ApiResponse(responseCode = "404", description = "No Route Profile has this name")
  RouteProfileDetail routeProfile(@PathVariable String name) {
    return editor.find(name);
  }

  @PutMapping(path = "/{name}", consumes = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      operationId = "putRouteProfile",
      summary = "Adds or replaces one Route Profile",
      description = "The body is its middleware in Traefik's format, such as {\"headers\": {...}}.")
  @ApiResponse(responseCode = "200", description = "Saved", useReturnTypeSchema = true)
  @ApiResponse(responseCode = "400", description = REFUSED)
  RouteProfileDetail putRouteProfile(
      @PathVariable String name, @RequestBody Map<String, Object> definition) {
    editor.save(Map.of(name, definition));
    return editor.find(name);
  }

  @DeleteMapping("/{name}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(operationId = "deleteRouteProfile", summary = "Removes one Route Profile")
  @ApiResponse(responseCode = "204", description = "Removed")
  @ApiResponse(responseCode = "404", description = "No Route Profile has this name")
  @ApiResponse(
      responseCode = "409",
      description = "A Publication or a chain names it. The services and chains give their names")
  void deleteRouteProfile(@PathVariable String name) {
    editor.remove(name);
  }
}
