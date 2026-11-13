package cc.lvhy.yard.dashboard.web;

import cc.lvhy.yard.dashboard.TraefikSettings;
import cc.lvhy.yard.dashboard.Workstation;
import cc.lvhy.yard.dashboard.services.ServiceState;
import cc.lvhy.yard.dashboard.services.UnknownStateException;
import cc.lvhy.yard.dashboard.services.YardReader;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

/** The Catalog page and its filters. */
@Controller
class PagesController {

  private final Workstation workstation;

  private final YardReader yard;

  private final TraefikSettings traefik;

  PagesController(Workstation workstation, YardReader yard, TraefikSettings traefik) {
    this.workstation = workstation;
    this.yard = yard;
    this.traefik = traefik;
  }

  @GetMapping(path = "/", produces = MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  String catalog(
      @RequestParam(required = false) String workstation,
      @RequestParam(required = false) String state,
      @RequestParam(required = false) String scope) {
    ServiceState only;
    try {
      only = blank(state) ? null : ServiceState.parse(state);
    } catch (UnknownStateException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
    }
    CatalogPage.ScopeFilter shared =
        blank(scope)
            ? CatalogPage.ScopeFilter.IN_RANGE
            : CatalogPage.ScopeFilter.named(scope)
                .orElseThrow(() ->
                    new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "The Scope " + scope + " is unknown. Use in-range, every, local or yard."));
    return CatalogPage.render(
        this.workstation,
        yard.yard(),
        new CatalogPage.Filters(blank(workstation) ? null : workstation, only, shared),
        traefik);
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
